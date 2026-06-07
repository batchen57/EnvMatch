package com.envmatch.service;

import com.envmatch.model.AIModel;
import com.envmatch.model.Task;
import com.envmatch.model.TaskResult;
import com.envmatch.model.TaskStatus;
import com.envmatch.repository.AIModelRepository;
import com.envmatch.repository.TaskRepository;
import com.envmatch.repository.TaskResultRepository;
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

@Service
public class TaskProcessingService {
    private final TaskRepository taskRepository;
    private final TaskResultRepository resultRepository;
    private final AIModelRepository modelRepository;
    private final VideoService videoService;
    private final AiAnalysisService aiAnalysisService;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final String webhookUrl;

    public TaskProcessingService(TaskRepository taskRepository,
                                 TaskResultRepository resultRepository,
                                 AIModelRepository modelRepository,
                                 VideoService videoService,
                                 AiAnalysisService aiAnalysisService,
                                 ObjectMapper mapper,
                                 @Value("${envmatch.webhook-url:${WEBHOOK_URL:}}") String webhookUrl) {
        this.taskRepository = taskRepository;
        this.resultRepository = resultRepository;
        this.modelRepository = modelRepository;
        this.videoService = videoService;
        this.aiAnalysisService = aiAnalysisService;
        this.mapper = mapper;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    // 使用有界任务执行器，避免采用 Spring 默认的不受限异步执行方式。
    @Async("taskExecutor")
    public void processAsync(String taskId) {
        try {
            Task task = taskRepository.findById(taskId).orElse(null);
            if (task == null) return;
            task.setStatus(TaskStatus.PROCESSING);
            taskRepository.save(task);

            JsonNode opts = task.getPreprocessOptions();
            String samplingType = text(opts, "sampling_type", "fixed");
            int fps = intVal(opts, "sampling_fps", 1);
            int resolution = intVal(opts, "resolution_val", 720);
            String recognitionMode = text(opts, "recognition_mode", "image");
            // One interval contract drives preview frames, image-model inputs, and native video-model payloads.
            double clipStart = doubleVal(opts, "clip_start_seconds", 0.0);
            double clipEnd = doubleVal(opts, "clip_end_seconds", 15.0);

            VideoMetadata metaA = videoService.metadata(task.getVideoAPath());
            VideoMetadata metaB = videoService.metadata(task.getVideoBPath());
            task.setVideoADuration(metaA.duration());
            task.setVideoAResolution(metaA.resolution());
            task.setVideoASize(metaA.sizeMb());
            task.setVideoBDuration(metaB.duration());
            task.setVideoBResolution(metaB.resolution());
            task.setVideoBSize(metaB.sizeMb());
            taskRepository.save(task);

            validateClipStart(clipStart, metaA, "A");
            validateClipStart(clipStart, metaB, "B");
            List<String> framesA = videoService.extractFrames(task.getVideoAPath(), taskId, "A", fps, resolution,
                    samplingType, clipStart, clipEnd);
            List<String> framesB = videoService.extractFrames(task.getVideoBPath(), taskId, "B", fps, resolution,
                    samplingType, clipStart, clipEnd);
            savePartialResult(taskId, framesA, framesB);

            String payloadA = task.getVideoAPath();
            String payloadB = task.getVideoBPath();
            if ("video".equalsIgnoreCase(recognitionMode)) {
                // Always create bounded payloads, even when resolution scaling is disabled.
                boolean resize = boolVal(opts, "resolution", false);
                payloadA = videoService.preprocessVideo(task.getVideoAPath(), taskId, "A", resolution,
                        resize, clipStart, clipEnd);
                payloadB = videoService.preprocessVideo(task.getVideoBPath(), taskId, "B", resolution,
                        resize, clipStart, clipEnd);
                VideoMetadata processedA = videoService.metadata(payloadA);
                VideoMetadata processedB = videoService.metadata(payloadB);
                task.setVideoAResolution(processedA.resolution());
                task.setVideoASize(processedA.sizeMb());
                task.setVideoBResolution(processedB.resolution());
                task.setVideoBSize(processedB.sizeMb());
                taskRepository.save(task);
            }

            if ("image".equalsIgnoreCase(recognitionMode)) {
                PayloadInfo infoA = payloadInfo(framesA);
                PayloadInfo infoB = payloadInfo(framesB);
                task.setVideoAResolution(infoA.resolution());
                task.setVideoASize(infoA.sizeMb());
                task.setVideoBResolution(infoB.resolution());
                task.setVideoBSize(infoB.sizeMb());
                taskRepository.save(task);
            }

            AIModel model = modelRepository.findFirstByIdentifier(task.getModelId()).orElse(null);
            AiAnalysisResponse response = aiAnalysisService.analyze(
                    framesA, framesB, task.getPrompt(),
                    model == null ? "" : model.getApiKey(),
                    model == null ? "" : model.getBaseUrl(),
                    task.getModelId(),
                    model == null ? "Unknown" : model.getProvider(),
                    recognitionMode, payloadA, payloadB, task.getId(), task.getTaskName()
            );

            JsonNode result = response.result();
            JsonNode usage = response.usage();
            task.setSimilarityScore(result.path("similarity_score").asDouble(0.0));
            task.setInputTokens(usage.path("prompt_tokens").asDouble(usage.path("input_tokens").asDouble(0.0)));
            task.setOutputTokens(usage.path("completion_tokens").asDouble(usage.path("output_tokens").asDouble(0.0)));
            task.setStatus(response.error() == null ? TaskStatus.COMPLETED : TaskStatus.FAILED);

            TaskResult taskResult = resultRepository.findById(taskId).orElseGet(() -> {
                TaskResult r = new TaskResult();
                r.setTaskId(taskId);
                return r;
            });
            taskResult.setDimensionScores(result.path("dimension_scores"));
            taskResult.setSimilarPoints(result.path("similar_points"));
            taskResult.setDifferencePoints(result.path("difference_points"));
            taskResult.setSummary(result.path("summary").asText(""));
            taskResult.setKeyFramesA(toArray(framesA));
            taskResult.setKeyFramesB(toArray(framesB));
            taskResult.setErrorMessage(response.error());
            taskResult.setInputTokens(task.getInputTokens());
            taskResult.setOutputTokens(task.getOutputTokens());

            resultRepository.save(taskResult);
            taskRepository.save(task);
            sendWebhook(task, taskResult);
        } catch (Exception e) {
            failTask(taskId, e.getMessage());
        }
    }

    private void savePartialResult(String taskId, List<String> framesA, List<String> framesB) {
        TaskResult result = resultRepository.findById(taskId).orElseGet(() -> {
            TaskResult r = new TaskResult();
            r.setTaskId(taskId);
            return r;
        });
        result.setSummary("处理中...");
        result.setKeyFramesA(toArray(framesA));
        result.setKeyFramesB(toArray(framesB));
        resultRepository.save(result);
    }

    private void failTask(String taskId, String message) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus(TaskStatus.FAILED);
            taskRepository.save(task);
        }
        TaskResult result = resultRepository.findById(taskId).orElseGet(() -> {
            TaskResult r = new TaskResult();
            r.setTaskId(taskId);
            return r;
        });
        result.setErrorMessage(message);
        result.setSummary("分析过程终止: " + message);
        resultRepository.save(result);
        if (task != null) sendWebhook(task, result);
    }

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
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    private ArrayNode toArray(List<String> values) {
        ArrayNode array = mapper.createArrayNode();
        values.forEach(array::add);
        return array;
    }

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

    private String text(JsonNode node, String key, String fallback) {
        return node != null && node.has(key) ? node.path(key).asText(fallback) : fallback;
    }

    private int intVal(JsonNode node, String key, int fallback) {
        return node != null && node.has(key) ? node.path(key).asInt(fallback) : fallback;
    }

    private double doubleVal(JsonNode node, String key, double fallback) {
        return node != null && node.has(key) ? node.path(key).asDouble(fallback) : fallback;
    }

    private void validateClipStart(double clipStart, VideoMetadata metadata, String suffix) {
        if (metadata.duration() > 0 && clipStart >= metadata.duration()) {
            throw new IllegalArgumentException("Clip start must be before the end of video " + suffix);
        }
    }

    private boolean boolVal(JsonNode node, String key, boolean fallback) {
        return node != null && node.has(key) ? node.path(key).asBoolean(fallback) : fallback;
    }

    private record PayloadInfo(String resolution, double sizeMb) {
    }
}
