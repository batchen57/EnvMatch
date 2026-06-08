package com.envmatch.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.envmatch.model.AIModel;
import com.envmatch.model.ModelCallLog;
import com.envmatch.model.PromptTemplate;
import com.envmatch.model.Task;
import com.envmatch.model.TaskResult;
import com.envmatch.model.TaskStatus;
import com.envmatch.mapper.AIModelMapper;
import com.envmatch.mapper.ModelCallLogMapper;
import com.envmatch.mapper.PromptTemplateMapper;
import com.envmatch.mapper.TaskMapper;
import com.envmatch.mapper.TaskResultMapper;
import com.envmatch.service.SeedService;
import com.envmatch.service.StorageService;
import com.envmatch.service.TaskProcessingService;
import com.envmatch.web.dto.AIModelRequest;
import com.envmatch.web.dto.PromptTemplateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.http.MediaType;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * 系统核心 API REST 控制器。
 * 
 * <p>该类暴露了系统所有的 HTTP REST 接口，包含了以下主要功能路由：
 * <ul>
 *   <li><b>任务管理</b>：创建异步比对任务（支持 Multipart 视频上传和预处理参数验证）、查询单个任务详情/进度、删除任务（同步清理物理磁盘文件）以及分页检索任务。</li>
 *   <li><b>仪表盘统计（Dashboard）</b>：汇总计算全部任务的总数、成功/失败分布、多维度相似度评分均值、历史 Token 吞吐曲线等指标。</li>
 *   <li><b>模型与提示词配置 CRUD</b>：动态管理 AI 模型端点和反欺诈 Prompts。</li>
 *   <li><b>调用日志审计查询</b>：支持关键词对模型调用审计记录进行检索和明细展示。</li>
 *   <li><b>文件清理调度</b>：提供接口清理指定天数前的遗留物理临时素材。</li>
 * </ul>
 * </p>
 */
@RestController
public class ApiController {
    
    /** 任务 Mapper */
    private final TaskMapper taskMapper;
    
    /** 任务结果 Mapper */
    private final TaskResultMapper resultMapper;
    
    /** 模型配置 Mapper */
    private final AIModelMapper modelMapper;
    
    /** 提示词模板 Mapper */
    private final PromptTemplateMapper promptMapper;
    
    /** 模型调用审计日志 Mapper */
    private final ModelCallLogMapper logMapper;
    
    /** 存储管理服务 */
    private final StorageService storageService;
    
    /** 异步任务处理服务 */
    private final TaskProcessingService taskProcessingService;
    
    /** 种子数据初始化服务 */
    private final SeedService seedService;
    
    /** Jackson 对象转换映射器 */
    private final ObjectMapper mapper;

    /**
     * 构造函数，自动注入 Spring 容器管理的依赖组件。
     */
    public ApiController(TaskMapper taskMapper,
                         TaskResultMapper resultMapper,
                         AIModelMapper modelMapper,
                         PromptTemplateMapper promptMapper,
                         ModelCallLogMapper logMapper,
                         StorageService storageService,
                         TaskProcessingService taskProcessingService,
                         SeedService seedService,
                         ObjectMapper mapper) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.modelMapper = modelMapper;
        this.promptMapper = promptMapper;
        this.logMapper = logMapper;
        this.storageService = storageService;
        this.taskProcessingService = taskProcessingService;
        this.seedService = seedService;
        this.mapper = mapper;
    }

    /**
     * 创建异步环境比对分析任务。
     * 接收上传的 A/B 两个媒体素材文件，并启动异步处理线程池进行抽帧和推理。
     *
     * @param taskName          任务名称
     * @param videoA            视频/图片素材 A
     * @param videoB            视频/图片素材 B
     * @param prompt            对比提示词（可选，若为空自动回落到系统预置的默认/反欺诈提示词）
     * @param modelId           使用的 AI 模型 ID 标识
     * @param preprocessOptions 预处理选项参数（JSON 字符串格式，包括识别模式、采样率、裁剪区间等）
     * @return 包含 taskId 的状态 Response Map
     */
    @PostMapping(value = "/tasks/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> createTask(@RequestParam("task_name") String taskName,
                                          @RequestPart("video_a") MultipartFile videoA,
                                          @RequestPart("video_b") MultipartFile videoB,
                                          @RequestParam(value = "prompt", required = false) String prompt,
                                          @RequestParam(value = "model_id", defaultValue = "gemini-2.5-pro") String modelId,
                                          @RequestParam(value = "preprocess_options", required = false) String preprocessOptions) {
        // 1. 输入数据校验
        validateText("task_name", taskName, 1, 200);
        validateText("model_id", modelId, 1, 200);
        if (videoA.isEmpty() || videoB.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Both video_a and video_b are required");
        }

        String taskId = UUID.randomUUID().toString();
        Path aPath = null;
        Path bPath = null;
        Task task = new Task();
        task.setId(taskId);
        task.setTaskName(taskName);
        task.setStatus(TaskStatus.PENDING);
        task.setModelId(modelId);

        // 2. 如果提示词为空，拉取最新的系统预置种子提示词填充
        String taskPrompt = prompt;
        if (taskPrompt == null || taskPrompt.isBlank()) {
            seedService.ensurePrompts();
            List<PromptTemplate> templates = promptMapper.selectList(new LambdaQueryWrapper<PromptTemplate>()
                    .orderByDesc(PromptTemplate::getCreatedAt));
            if (!templates.isEmpty()) {
                PromptTemplate defaultTemplate = templates.stream()
                        .filter(t -> "系统默认通用提示词".equals(t.getName()))
                        .findFirst()
                        .orElse(templates.get(0));
                taskPrompt = defaultTemplate.getContent();
            }
        }
        task.setPrompt(taskPrompt);
        task.setPreprocessOptions(parsePreprocessOptions(preprocessOptions));

        try {
            // 先保存两个上传文件再创建任务记录；若持久化或任务入队失败，触发补偿清理已保存的物理磁盘文件。
            aPath = storageService.saveUpload(videoA, taskId, "A");
            bPath = storageService.saveUpload(videoB, taskId, "B");
            task.setVideoAPath(aPath.toString());
            task.setVideoBPath(bPath.toString());
            saveOrUpdateTask(task);
            
            // 3. 提交至异步线程池开始处理
            taskProcessingService.processAsync(taskId);
            return Map.of("task_id", taskId, "message", "Task created successfully");
        } catch (TaskRejectedException e) {
            // 补偿逻辑：线程池队列满被拒绝时，同步删除物理上传文件并清理数据库占位记录
            cleanupFailedCreation(taskId, aPath, bPath);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Task queue is full, please try again later", e);
        } catch (IOException | RuntimeException e) {
            cleanupFailedCreation(taskId, aPath, bPath);
            if (e instanceof ResponseStatusException responseStatusException) throw responseStatusException;
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to create task", e);
        }
    }

    /**
     * 根据任务 ID 获取任务及比对分析结果明细。
     *
     * @param taskId 任务 ID
     * @return 任务及其结果构成的实体 Map
     */
    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> getTask(@PathVariable String taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(NOT_FOUND, "Task not found");
        }
        TaskResult result = resultMapper.selectById(taskId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task", task);
        response.put("result", result);
        return response;
    }

    /**
     * 删除指定任务。
     * 会同步删除磁盘上保存的原始视频、切片视频以及抽帧预览图片，最后删除任务及分析结果的数据库记录。
     *
     * @param taskId 任务 ID
     * @return 操作状态
     */
    @DeleteMapping("/tasks/{taskId}")
    public Map<String, String> deleteTask(@PathVariable String taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(NOT_FOUND, "Task not found");
        }
        // 同步删除对应的物理媒体文件，释放存储空间
        deletePath(task.getVideoAPath());
        deletePath(task.getVideoBPath());
        deletePath(storageService.storageDir().resolve(taskId + "_A_frames").toString());
        deletePath(storageService.storageDir().resolve(taskId + "_B_frames").toString());
        deletePath(storageService.storageDir().resolve(taskId + "_A_processed.mp4").toString());
        deletePath(storageService.storageDir().resolve(taskId + "_B_processed.mp4").toString());
        
        resultMapper.deleteById(taskId);
        taskMapper.deleteById(taskId);
        return Map.of("status", "success", "message", "Task " + taskId + " deleted");
    }

    /**
     * 分页列出系统中的分析任务，支持根据状态过滤，同时返回各类状态任务的总数。
     *
     * @param status 过滤状态（如 ALL, PROCESSING, COMPLETED, FAILED）
     * @param skip   跳过的记录数
     * @param limit  获取的最大行数
     * @return 包含任务列表和统计计数的 Map
     */
    @GetMapping("/tasks/")
    public Map<String, Object> listTasks(@RequestParam(value = "status", required = false) String status,
                                         @RequestParam(defaultValue = "0") int skip,
                                         @RequestParam(defaultValue = "100") int limit) {
        int safeSkip = Math.max(0, skip);
        int safeLimit = Math.min(500, Math.max(1, limit));
        List<Task> tasks;
        if (status == null || status.equals("ALL")) {
            tasks = taskMapper.findPage(safeSkip, safeLimit);
        } else if (status.equals("PROCESSING")) {
            tasks = taskMapper.findProcessingPage(safeSkip, safeLimit);
        } else {
            try {
                TaskStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(BAD_REQUEST, "Unsupported task status: " + status);
            }
            tasks = taskMapper.findPageByStatus(status, safeSkip, safeLimit);
        }

        // 组装统计计数
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", (long) taskMapper.selectCount(null));
        counts.put("PROCESSING", (long) taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .in(Task::getStatus, List.of(TaskStatus.PENDING, TaskStatus.PROCESSING))));
        counts.put("COMPLETED", (long) taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, TaskStatus.COMPLETED)));
        counts.put("FAILED", (long) taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, TaskStatus.FAILED)));
        return Map.of("tasks", tasks, "total_counts", counts);
    }

    /**
     * 汇总生成仪表盘统计数据。
     * 统计指标包括：任务总量、成功率、平均相似度、总 Token 消耗、总素材体积和时长、五维度雷达图数据、模型分布饼图、以及最近 7 日趋势。
     *
     * @return 指标数据 Map
     */
    @GetMapping("/dashboard-stats")
    public Map<String, Object> dashboardStats() {
        List<Task> tasks = taskMapper.selectList(null);
        List<Task> completed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).toList();
        double avgSimilarity = completed.stream().map(Task::getSimilarityScore).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0.0);
        double totalTokens = tasks.stream().mapToDouble(t -> nz(t.getInputTokens()) + nz(t.getOutputTokens())).sum();
        double totalDuration = tasks.stream().mapToDouble(t -> nz(t.getVideoADuration()) + nz(t.getVideoBDuration())).sum();
        double totalSize = tasks.stream().mapToDouble(t -> nz(t.getVideoASize()) + nz(t.getVideoBSize())).sum();

        // 相似度阈值分布
        Map<String, Integer> distribution = Map.of(
                "high", (int) tasks.stream().filter(t -> t.getSimilarityScore() != null && t.getSimilarityScore() >= 70).count(),
                "medium", (int) tasks.stream().filter(t -> t.getSimilarityScore() != null && t.getSimilarityScore() >= 40 && t.getSimilarityScore() < 70).count(),
                "low", (int) tasks.stream().filter(t -> t.getSimilarityScore() != null && t.getSimilarityScore() < 40).count()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", tasks.size());
        response.put("completed", completed.size());
        response.put("failed", tasks.stream().filter(t -> t.getStatus() == TaskStatus.FAILED).count());
        response.put("avg_similarity", round(avgSimilarity, 1));
        response.put("total_tokens", (int) totalTokens);
        response.put("total_duration", round(totalDuration, 1));
        response.put("total_size", round(totalSize, 1));
        response.put("avg_dimensions", averageDimensions(resultMapper.selectList(null)));
        response.put("models_summary", modelSummary(tasks));
        response.put("distribution", distribution);
        response.put("trend", trend(tasks));
        return response;
    }

    /**
     * 临时磁盘文件清理接口。
     * 清理物理存储根目录下，修改时间长于指定天数的所有临时及生成素材。
     *
     * @param days 阀值天数，默认为 7 天
     * @return 清理掉的文件数量
     */
    @PostMapping("/cleanup")
    public Map<String, Object> cleanup(@RequestParam(defaultValue = "7") int days) {
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        int deleted = 0;
        Path root = storageService.storageDir();
        if (Files.exists(root)) {
            try (var stream = Files.list(root)) {
                for (Path path : stream.toList()) {
                    try {
                        if (Files.getLastModifiedTime(path).toMillis() < cutoff) {
                            deletePath(path.toString());
                            deleted++;
                        }
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return Map.of("status", "ok", "deleted", deleted);
    }

    /**
     * 列出所有的提示词模板列表（若为空自动初始化预置模板）。
     */
    @GetMapping("/prompt-templates/")
    public List<PromptTemplate> listPromptTemplates() {
        seedService.ensurePrompts();
        return promptMapper.selectList(new LambdaQueryWrapper<PromptTemplate>()
                .orderByDesc(PromptTemplate::getCreatedAt));
    }

    /**
     * 创建一个新的提示词模板。
     */
    @PostMapping("/prompt-templates/")
    public PromptTemplate createPromptTemplate(@RequestBody PromptTemplateRequest body) {
        validatePromptRequest(body);
        PromptTemplate template = new PromptTemplate();
        template.setName(body.name().trim());
        template.setContent(body.content());
        promptMapper.insert(template);
        return template;
    }

    /**
     * 更新指定的提示词模板。
     */
    @PutMapping("/prompt-templates/{templateId}")
    public PromptTemplate updatePromptTemplate(@PathVariable String templateId,
                                               @RequestBody PromptTemplateRequest body) {
        validatePromptRequest(body);
        PromptTemplate template = promptMapper.selectById(templateId);
        if (template == null) {
            throw new ResponseStatusException(NOT_FOUND, "Template not found");
        }
        template.setName(body.name().trim());
        template.setContent(body.content());
        promptMapper.updateById(template);
        return template;
    }

    /**
     * 删除指定的提示词模板。
     */
    @DeleteMapping("/prompt-templates/{templateId}")
    public Map<String, String> deletePromptTemplate(@PathVariable String templateId) {
        if (promptMapper.selectById(templateId) == null) {
            throw new ResponseStatusException(NOT_FOUND, "Template not found");
        }
        promptMapper.deleteById(templateId);
        return Map.of("status", "ok");
    }

    /**
     * 列出所有注册在系统的 AI 模型配置列表（若表为空自动注入种子数据）。
     */
    @GetMapping("/models/")
    public List<AIModel> listModels() {
        seedService.ensureModels();
        return modelMapper.selectList(new LambdaQueryWrapper<AIModel>()
                .orderByAsc(AIModel::getSortOrder)
                .orderByDesc(AIModel::getCreatedAt));
    }

    /**
     * 添加一个新的 AI 模型配置。
     */
    @PostMapping("/models/")
    public AIModel createModel(@RequestBody AIModelRequest body) {
        validateModelRequest(body);
        validateCapabilities(body.capabilities());
        if ("true".equals(body.isDefaultOrFalse())) clearDefaults(null);
        AIModel model = new AIModel();
        applyModelRequest(model, body);
        modelMapper.insert(model);
        return model;
    }

    /**
     * 修改一个现有的 AI 模型配置。
     */
    @PutMapping("/models/{modelId}")
    public AIModel updateModel(@PathVariable String modelId, @RequestBody AIModelRequest body) {
        validateModelRequest(body);
        validateCapabilities(body.capabilities());
        AIModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new ResponseStatusException(NOT_FOUND, "Model not found");
        }
        if ("true".equals(body.isDefaultOrFalse())) clearDefaults(modelId);
        applyModelRequest(model, body);
        modelMapper.updateById(model);
        return model;
    }

    /**
     * 删除指定的 AI 模型配置。
     */
    @DeleteMapping("/models/{modelId}")
    public Map<String, String> deleteModel(@PathVariable String modelId) {
        if (modelMapper.selectById(modelId) == null) {
            throw new ResponseStatusException(NOT_FOUND, "Model not found");
        }
        modelMapper.deleteById(modelId);
        return Map.of("status", "ok");
    }

    /**
     * 分页多维度模糊搜索模型接口调用审计日志。
     *
     * @param search 搜索关键词（支持匹配任务名、任务ID、模型ID）
     * @param skip   跳过数
     * @param limit  返回行数限制
     * @return 日志列表及匹配总量 Map
     */
    @GetMapping("/model-logs/")
    public Map<String, Object> listModelLogs(@RequestParam(value = "search", required = false) String search,
                                             @RequestParam(defaultValue = "0") int skip,
                                             @RequestParam(defaultValue = "10") int limit) {
        int safeSkip = Math.max(0, skip);
        int safeLimit = Math.min(500, Math.max(1, limit));
        List<ModelCallLog> logs;
        long total;
        if (search == null || search.isBlank()) {
            logs = logMapper.findPage(safeSkip, safeLimit);
            total = logMapper.selectCount(null);
        } else {
            String pattern = "%" + search.trim() + "%";
            logs = logMapper.searchPage(pattern, safeSkip, safeLimit);
            total = logMapper.countSearch(pattern);
        }
        return Map.of("logs", logs, "total", total);
    }

    /**
     * 根据 ID 获取单个模型调用审计日志详情（包括完整的请求入参与返回响应 Json，排除了 Base64 大视频数据）。
     */
    @GetMapping("/model-logs/{id}")
    public ModelCallLog getModelLog(@PathVariable Long id) {
        ModelCallLog log = logMapper.selectById(id);
        if (log == null) {
            throw new ResponseStatusException(NOT_FOUND, "Model call log not found");
        }
        return log;
    }

    /**
     * 校验和解析预处理选项参数。
     */
    private JsonNode parsePreprocessOptions(String raw) {
        if (raw == null || raw.isBlank()) return NullNode.getInstance();
        try {
            JsonNode node = mapper.readTree(raw);
            if (!node.isObject()) throw new IllegalArgumentException("preprocess_options must be a JSON object");
            validateChoice(node, "recognition_mode", List.of("image", "video"));
            validateChoice(node, "sampling_type", List.of("fixed", "perceptual"));
            validateRange(node, "sampling_fps", 1, 5);
            validateRange(node, "resolution_val", 90, 2160);
            // 默认为旧客户端保留 0 - 15 秒裁剪限制
            validateDoubleRange(node, "clip_start_seconds", 0.0, 86400.0);
            validateDoubleRange(node, "clip_end_seconds", 0.001, 86400.0);
            double clipStart = node.path("clip_start_seconds").asDouble(0.0);
            double clipEnd = node.path("clip_end_seconds").asDouble(15.0);
            if (clipEnd <= clipStart) {
                throw new IllegalArgumentException("clip_end_seconds must be greater than clip_start_seconds");
            }
            if (node.has("resolution") && !node.path("resolution").isBoolean()) {
                throw new IllegalArgumentException("resolution must be a boolean");
            }
            return node;
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid preprocess_options: " + e.getMessage(), e);
        }
    }

    private void validateChoice(JsonNode node, String key, List<String> allowed) {
        if (node.has(key) && !allowed.contains(node.path(key).asText())) {
            throw new IllegalArgumentException(key + " must be one of " + allowed);
        }
    }

    private void validateRange(JsonNode node, String key, int min, int max) {
        if (!node.has(key)) return;
        if (!node.path(key).canConvertToInt()) throw new IllegalArgumentException(key + " must be an integer");
        int value = node.path(key).asInt();
        if (value < min || value > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
    }

    private void validateDoubleRange(JsonNode node, String key, double min, double max) {
        if (!node.has(key)) return;
        if (!node.path(key).isNumber()) throw new IllegalArgumentException(key + " must be a number");
        double value = node.path(key).asDouble();
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
    }

    private void validateCapabilities(JsonNode capabilities) {
        if (capabilities == null || capabilities.isNull()) return;
        if (!capabilities.isArray()) throw new ResponseStatusException(BAD_REQUEST, "capabilities must be an array");
        for (JsonNode capability : capabilities) {
            if (!capability.isTextual() || !List.of("text", "image", "video").contains(capability.asText())) {
                throw new ResponseStatusException(BAD_REQUEST, "Unsupported model capability");
            }
        }
    }

    private void validatePromptRequest(PromptTemplateRequest body) {
        if (body == null) throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        validateText("name", body.name(), 1, 200);
        validateText("content", body.content(), 1, 100_000);
    }

    private void validateModelRequest(AIModelRequest body) {
        if (body == null) throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        validateText("name", body.name(), 1, 200);
        validateText("identifier", body.identifier(), 1, 200);
        validateText("provider", body.provider(), 1, 100);
        validateText("api_key", body.apiKeyOrEmpty(), 0, 10_000);
        validateText("base_url", body.baseUrlOrEmpty(), 0, 2_000);
        validateText("description", body.descriptionOrEmpty(), 0, 10_000);
        if (!List.of("true", "false").contains(body.isDefaultOrFalse())) {
            throw new ResponseStatusException(BAD_REQUEST, "is_default must be true or false");
        }
        if (body.sortOrder() != null && !Double.isFinite(body.sortOrder())) {
            throw new ResponseStatusException(BAD_REQUEST, "sort_order must be finite");
        }
    }

    private void validateText(String field, String value, int minLength, int maxLength) {
        int length = value == null ? 0 : value.trim().length();
        if (length < minLength || length > maxLength) {
            throw new ResponseStatusException(BAD_REQUEST,
                    field + " length must be between " + minLength + " and " + maxLength);
        }
    }

    private void applyModelRequest(AIModel model, AIModelRequest body) {
        model.setName(body.name().trim());
        model.setIdentifier(body.identifier().trim());
        model.setProvider(body.provider().trim());
        model.setApiKey(body.apiKeyOrEmpty());
        model.setBaseUrl(body.baseUrlOrEmpty());
        model.setDescription(body.descriptionOrEmpty());
        model.setCapabilities(body.capabilities());
        model.setIsDefault(body.isDefaultOrFalse());
        model.setSortOrder(body.sortOrderOrZero());
    }

    private void cleanupFailedCreation(String taskId, Path aPath, Path bPath) {
        // 由于本地多媒体写入与数据库持久化不能共享事务，此方法提供幂等性清理以防数据和文件冗余遗留
        deletePath(aPath == null ? null : aPath.toString());
        deletePath(bPath == null ? null : bPath.toString());
        resultMapper.deleteById(taskId);
        taskMapper.deleteById(taskId);
    }

    private void clearDefaults(String exceptId) {
        List<AIModel> defaults = modelMapper.selectList(new LambdaQueryWrapper<AIModel>()
                .eq(AIModel::getIsDefault, "true"));
        for (AIModel model : defaults) {
            if (exceptId == null || !exceptId.equals(model.getId())) {
                model.setIsDefault("false");
                modelMapper.updateById(model);
            }
        }
    }

    private void saveOrUpdateTask(Task task) {
        if (taskMapper.selectById(task.getId()) == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
    }

    private void deletePath(String raw) {
        if (raw == null || raw.isBlank()) return;
        Path path = Path.of(raw);
        if (!Files.exists(path)) return;
        try {
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }

    private double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    private Map<String, Double> averageDimensions(List<TaskResult> results) {
        List<String> keys = List.of("indoor_layout", "wall_floor_material", "furniture_fixtures", "window_door_style", "lighting_environment");
        Map<String, Double> sums = new LinkedHashMap<>();
        keys.forEach(k -> sums.put(k, 0.0));
        if (results.isEmpty()) return sums;
        for (TaskResult result : results) {
            JsonNode dims = result.getDimensionScores();
            for (String key : keys) sums.put(key, sums.get(key) + (dims == null ? 0 : dims.path(key).asDouble(0.0)));
        }
        Map<String, Double> averages = new LinkedHashMap<>();
        for (String key : keys) averages.put(key, round(sums.get(key) / results.size(), 1));
        return averages;
    }

    private List<Map<String, Object>> modelSummary(List<Task> tasks) {
        Map<String, List<Task>> grouped = new HashMap<>();
        for (Task task : tasks) grouped.computeIfAbsent(task.getModelId() == null ? "Unknown" : task.getModelId(), k -> new ArrayList<>()).add(task);
        List<Map<String, Object>> summary = new ArrayList<>();
        for (Map.Entry<String, List<Task>> entry : grouped.entrySet()) {
            double avg = entry.getValue().stream().map(Task::getSimilarityScore).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0.0);
            summary.add(Map.of("model_id", entry.getKey(), "count", entry.getValue().size(), "avg_similarity", round(avg, 1)));
        }
        return summary;
    }

    private Map<String, Object> trend(List<Task> tasks) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<String> dates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<Double> similarities = new ArrayList<>();
        List<Integer> tokens = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = LocalDateTime.of(day, LocalTime.MIN);
            LocalDateTime end = LocalDateTime.of(day, LocalTime.MAX);
            List<Task> dayTasks = tasks.stream().filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(start) && !t.getCreatedAt().isAfter(end)).toList();
            dates.add(day.format(fmt));
            counts.add(dayTasks.size());
            similarities.add(round(dayTasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                     .map(Task::getSimilarityScore).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0.0), 1));
            tokens.add((int) dayTasks.stream().mapToDouble(t -> nz(t.getInputTokens()) + nz(t.getOutputTokens())).sum());
        }
        return Map.of("dates", dates, "counts", counts, "similarities", similarities, "tokens", tokens);
    }
}
