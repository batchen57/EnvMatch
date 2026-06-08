package com.envmatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.envmatch.model.AIModel;
import com.envmatch.model.Task;
import com.envmatch.model.TaskResult;
import com.envmatch.model.TaskStatus;
import com.envmatch.mapper.AIModelMapper;
import com.envmatch.mapper.TaskMapper;
import com.envmatch.mapper.TaskResultMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 视频环境相似度对比异步任务处理核心服务。
 * 
 * <p>负责编排整个异步处理流水线，包括：
 * <ul>
 *   <li>任务状态流转（PENDING -> PROCESSING -> COMPLETED / FAILED）。</li>
 *   <li>调用 {@link VideoService} 探测 A/B 两段视频的元数据（分辨率、时长、大小）。</li>
 *   <li>根据处理选项（模式、FPS、裁剪起止时间、采样类型等）调用 {@link VideoService} 提取关键帧。</li>
 *   <li>可选用原生的“视频识别模式”，自动调用视频压缩编码逻辑限制文件大小以符合模型输入规范。</li>
 *   <li>查询对应的配置大模型，调用 {@link AiAnalysisService} 执行多模态推理。</li>
 *   <li>保存结构化分析得分、差异/相似特征证据等数据，并触发 Webhook 完成回调通知。</li>
 *   <li>以及多维度特征评估和计算推理等功能。</li>
 * </ul>
 * </p>
 */
@Service
public class TaskProcessingService {
    
    /** 任务数据访问 Mapper */
    private final TaskMapper taskMapper;
    
    /** 任务比对详细结果数据访问 Mapper */
    private final TaskResultMapper resultMapper;
    
    /** AI 模型配置数据访问 Mapper */
    private final AIModelMapper modelMapper;
    
    /** 媒体/视频帧抽取服务 */
    private final VideoService videoService;
    
    /** 大模型接口比对服务 */
    private final AiAnalysisService aiAnalysisService;
    
    /** JSON 对象解析与映射器 */
    private final ObjectMapper mapper;
    
    /** 发送 Webhook 调用的 HttpClient */
    private final HttpClient httpClient;
    
    /** 回调接口 URL 地址 */
    private final String webhookUrl;

    /**
     * 构造函数，由 Spring 自动注入依赖并读取配置文件中的回调配置。
     */
    public TaskProcessingService(TaskMapper taskMapper,
                                 TaskResultMapper resultMapper,
                                 AIModelMapper modelMapper,
                                 VideoService videoService,
                                 AiAnalysisService aiAnalysisService,
                                 ObjectMapper mapper,
                                 @Value("${envmatch.webhook-url:${WEBHOOK_URL:}}") String webhookUrl) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.modelMapper = modelMapper;
        this.videoService = videoService;
        this.aiAnalysisService = aiAnalysisService;
        this.mapper = mapper;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * 异步处理指定的对比分析任务。
     * 该方法由自定义线程池 "taskExecutor" 异步调用，确保主 Web 线程不会阻塞。
     *
     * @param taskId 待处理的任务 ID
     */
    // 使用有界任务执行器，避免采用 Spring 默认的不受限异步执行方式。
    @Async("taskExecutor")
    public void processAsync(String taskId) {
        try {
            // 1. 获取任务并更新其状态为 "PROCESSING" (处理中)
            Task task = taskMapper.selectById(taskId);
            if (task == null) return;
            task.setStatus(TaskStatus.PROCESSING);
            taskMapper.updateById(task);

            // 2. 解析预处理选项配置参数
            JsonNode opts = task.getPreprocessOptions();
            String samplingType = text(opts, "sampling_type", "fixed");
            int fps = intVal(opts, "sampling_fps", 1);
            int resolution = intVal(opts, "resolution_val", 720);
            String recognitionMode = text(opts, "recognition_mode", "image");
            
            // 时段裁剪参数：用于指定仅分析视频的某一时间段以节省模型 Token
            double clipStart = doubleVal(opts, "clip_start_seconds", 0.0);
            double clipEnd = doubleVal(opts, "clip_end_seconds", 15.0);

            // 3. 探测 A/B 两个视频素材的物理元数据
            VideoMetadata metaA = videoService.metadata(task.getVideoAPath());
            VideoMetadata metaB = videoService.metadata(task.getVideoBPath());
            task.setVideoADuration(metaA.duration());
            task.setVideoAResolution(metaA.resolution());
            task.setVideoASize(metaA.sizeMb());
            task.setVideoBDuration(metaB.duration());
            task.setVideoBResolution(metaB.resolution());
            task.setVideoBSize(metaB.sizeMb());
            taskMapper.updateById(task);

            // 4. 验证裁剪起始时间是否合法（不能超过视频总长度）
            validateClipStart(clipStart, metaA, "A");
            validateClipStart(clipStart, metaB, "B");
            
            // 5. 进行视频帧抽取（支持均匀固定抽帧或感知采样）
            List<String> framesA = videoService.extractFrames(task.getVideoAPath(), taskId, "A", fps, resolution,
                    samplingType, clipStart, clipEnd);
            List<String> framesB = videoService.extractFrames(task.getVideoBPath(), taskId, "B", fps, resolution,
                    samplingType, clipStart, clipEnd);
            
            // 6. 保存提取的预览帧数据以供前端实时轮询预览
            savePartialResult(taskId, framesA, framesB);

            String payloadA = task.getVideoAPath();
            String payloadB = task.getVideoBPath();
            
            // 7. 处理视频识别模式 (Native Video Mode)
            if ("video".equalsIgnoreCase(recognitionMode)) {
                // 即使不缩放，也必须执行视频截取和转码，以保持在用户设定的时间片段内
                boolean resize = boolVal(opts, "resolution", false);
                payloadA = videoService.preprocessVideo(task.getVideoAPath(), taskId, "A", resolution,
                        resize, clipStart, clipEnd);
                payloadB = videoService.preprocessVideo(task.getVideoBPath(), taskId, "B", resolution,
                        resize, clipStart, clipEnd);
                
                // 更新截取转码后的实际文件分辨率和文件大小
                VideoMetadata processedA = videoService.metadata(payloadA);
                VideoMetadata processedB = videoService.metadata(payloadB);
                task.setVideoAResolution(processedA.resolution());
                task.setVideoASize(processedA.sizeMb());
                task.setVideoBResolution(processedB.resolution());
                task.setVideoBSize(processedB.sizeMb());
                taskMapper.updateById(task);
            }

            // 8. 处理图片识别模式 (Image Grid Mode)
            if ("image".equalsIgnoreCase(recognitionMode)) {
                PayloadInfo infoA = payloadInfo(framesA);
                PayloadInfo infoB = payloadInfo(framesB);
                task.setVideoAResolution(infoA.resolution());
                task.setVideoASize(infoA.sizeMb());
                task.setVideoBResolution(infoB.resolution());
                task.setVideoBSize(infoB.sizeMb());
                taskMapper.updateById(task);
            }

            // 9. 查询所使用的 AI 模型配置
            AIModel model = modelMapper.selectOne(new LambdaQueryWrapper<AIModel>()
                    .eq(AIModel::getIdentifier, task.getModelId())
                    .last("LIMIT 1"));
            
            // 10. 发送请求给 AI 接口进行对比分析
            AiAnalysisResponse response = aiAnalysisService.analyze(
                    framesA, framesB, task.getPrompt(),
                    model == null ? "" : model.getApiKey(),
                    model == null ? "" : model.getBaseUrl(),
                    task.getModelId(),
                    model == null ? "Unknown" : model.getProvider(),
                    recognitionMode, payloadA, payloadB, task.getId(), task.getTaskName()
            );

            // 11. 处理分析响应，将其解析后写入数据库
            JsonNode result = response.result();
            JsonNode usage = response.usage();
            task.setSimilarityScore(result.path("similarity_score").asDouble(0.0));
            task.setInputTokens(usage.path("prompt_tokens").asDouble(usage.path("input_tokens").asDouble(0.0)));
            task.setOutputTokens(usage.path("completion_tokens").asDouble(usage.path("output_tokens").asDouble(0.0)));
            task.setStatus(response.error() == null ? TaskStatus.COMPLETED : TaskStatus.FAILED);

            TaskResult taskResult = resultMapper.selectById(taskId);
            if (taskResult == null) {
                taskResult = new TaskResult();
                taskResult.setTaskId(taskId);
            }
            taskResult.setDimensionScores(result.path("dimension_scores"));
            taskResult.setSimilarPoints(result.path("similar_points"));
            taskResult.setDifferencePoints(result.path("difference_points"));
            taskResult.setSummary(result.path("summary").asText(""));
            taskResult.setKeyFramesA(toArray(framesA));
            taskResult.setKeyFramesB(toArray(framesB));
            taskResult.setErrorMessage(response.error());
            taskResult.setInputTokens(task.getInputTokens());
            taskResult.setOutputTokens(task.getOutputTokens());

            // 12. 保存最终的对比结果并更新主任务表
            saveOrUpdateResult(taskResult);
            taskMapper.updateById(task);
            
            // 13. 发送 Webhook 完成回调通知
            sendWebhook(task, taskResult);
        } catch (Exception e) {
            // 任何未知异常均归类为任务失败，并记录错误详情
            failTask(taskId, e.getMessage());
        }
    }

    /**
     * 保存抽取的部分关键帧预览信息（中途写回，利于前端即时预览进度）。
     *
     * @param taskId  关联任务 ID
     * @param framesA 提取出的 A 视频抽帧文件路径列表
     * @param framesB 提取出的 B 视频抽帧文件路径列表
     */
    private void savePartialResult(String taskId, List<String> framesA, List<String> framesB) {
        TaskResult result = resultMapper.selectById(taskId);
        if (result == null) {
            result = new TaskResult();
            result.setTaskId(taskId);
        }
        result.setSummary("处理中...");
        result.setKeyFramesA(toArray(framesA));
        result.setKeyFramesB(toArray(framesB));
        saveOrUpdateResult(result);
    }

    /**
     * 将任务强制置为失败并持久化异常消息。
     *
     * @param taskId  关联任务 ID
     * @param message 失败说明
     */
    private void failTask(String taskId, String message) {
        Task task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.FAILED);
            taskMapper.updateById(task);
        }
        TaskResult result = resultMapper.selectById(taskId);
        if (result == null) {
            result = new TaskResult();
            result.setTaskId(taskId);
        }
        result.setErrorMessage(message);
        result.setSummary("分析过程终止: " + message);
        saveOrUpdateResult(result);
        if (task != null) sendWebhook(task, result);
    }

    /**
     * 写入或更新任务详细分析结果。
     *
     * @param result 结果实体对象
     */
    private void saveOrUpdateResult(TaskResult result) {
        if (resultMapper.selectById(result.getTaskId()) == null) {
            resultMapper.insert(result);
        } else {
            resultMapper.updateById(result);
        }
    }

    /**
     * 发送异步 Webhook 接口回调。
     *
     * @param task   主任务信息
     * @param result 最终分析结果
     */
    private void sendWebhook(Task task, TaskResult result) {
        if (webhookUrl.isBlank() || task == null) return;
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("task_id", task.getId());
            payload.put("task_name", task.getTaskName());
            payload.put("status", task.getStatus() == null ? "" : task.getStatus().name());
            if (task.getSimilarityScore() != null) payload.put("similarity_score", task.getSimilarityScore());
            if (task.getInputTokens() != null) payload.put("input_tokens", task.getInputTokens());
            if (task.getOutputTokens() != null) payload.put("output_tokens", task.getOutputTokens());
            if (result != null && result.getErrorMessage() != null) payload.put("error_message", result.getErrorMessage());
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            // 采用异步方式发送，即使接收方响应缓慢也不影响当前执行线程
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    /**
     * 将列表转换为 Jackson 格式的 JSON 数组节点。
     */
    private ArrayNode toArray(List<String> values) {
        ArrayNode array = mapper.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    /**
     * 依据抽帧文件大小及属性计算待发载荷的相关信息。
     */
    private PayloadInfo payloadInfo(List<String> frames) {
        if (frames == null || frames.isEmpty()) return new PayloadInfo("0x0", 0.0);
        String resolution = "unknown";
        long bytes = 0L;
        for (String frame : frames) {
            try {
                Path path = Path.of(frame);
                if (Files.exists(path)) {
                    bytes += Files.size(path);
                    if ("unknown".equals(resolution)) {
                        BufferedImage image = ImageIO.read(path.toFile());
                        if (image != null) resolution = image.getWidth() + "x" + image.getHeight();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new PayloadInfo(resolution, Math.round(bytes / 1024.0 / 1024.0 * 100.0) / 100.0);
    }

    /** 辅助解析 JSON String 参数的默认值回落工具 */
    private String text(JsonNode node, String key, String fallback) {
        return node != null && node.has(key) ? node.path(key).asText(fallback) : fallback;
    }

    /** 辅助解析 JSON Int 参数的默认值回落工具 */
    private int intVal(JsonNode node, String key, int fallback) {
        return node != null && node.has(key) ? node.path(key).asInt(fallback) : fallback;
    }

    /** 辅助解析 JSON Double 参数的默认值回落工具 */
    private double doubleVal(JsonNode node, String key, double fallback) {
        return node != null && node.has(key) ? node.path(key).asDouble(fallback) : fallback;
    }

    /** 校验时段裁剪起始时间是否合法 */
    private void validateClipStart(double clipStart, VideoMetadata metadata, String suffix) {
        if (metadata.duration() > 0 && clipStart >= metadata.duration()) {
            throw new IllegalArgumentException("Clip start must be before the end of video " + suffix);
        }
    }

    /** 辅助解析 JSON Boolean 参数的默认值回落工具 */
    private boolean boolVal(JsonNode node, String key, boolean fallback) {
        return node != null && node.has(key) ? node.path(key).asBoolean(fallback) : fallback;
    }

    /**
     * 表示图片或视频载荷的物理元数据记录。
     *
     * @param resolution 分辨率
     * @param sizeMb     大小
     */
    private record PayloadInfo(String resolution, double sizeMb) {
    }
}
