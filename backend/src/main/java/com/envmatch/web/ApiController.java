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

@RestController
public class ApiController {
    private final TaskMapper taskMapper;
    private final TaskResultMapper resultMapper;
    private final AIModelMapper modelMapper;
    private final PromptTemplateMapper promptMapper;
    private final ModelCallLogMapper logMapper;
    private final StorageService storageService;
    private final TaskProcessingService taskProcessingService;
    private final SeedService seedService;
    private final ObjectMapper mapper;

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

    @PostMapping(value = "/tasks/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> createTask(@RequestParam("task_name") String taskName,
                                          @RequestPart("video_a") MultipartFile videoA,
                                          @RequestPart("video_b") MultipartFile videoB,
                                          @RequestParam(value = "prompt", required = false) String prompt,
                                          @RequestParam(value = "model_id", defaultValue = "gemini-2.5-pro") String modelId,
                                          @RequestParam(value = "preprocess_options", required = false) String preprocessOptions) {
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
            // 先保存两个上传文件再创建任务记录；持久化或任务入队失败时，补偿清理所有已完成步骤。
            aPath = storageService.saveUpload(videoA, taskId, "A");
            bPath = storageService.saveUpload(videoB, taskId, "B");
            task.setVideoAPath(aPath.toString());
            task.setVideoBPath(bPath.toString());
            saveOrUpdateTask(task);
            taskProcessingService.processAsync(taskId);
            return Map.of("task_id", taskId, "message", "Task created successfully");
        } catch (TaskRejectedException e) {
            cleanupFailedCreation(taskId, aPath, bPath);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Task queue is full, please try again later", e);
        } catch (IOException | RuntimeException e) {
            cleanupFailedCreation(taskId, aPath, bPath);
            if (e instanceof ResponseStatusException responseStatusException) throw responseStatusException;
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to create task", e);
        }
    }

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

    @DeleteMapping("/tasks/{taskId}")
    public Map<String, String> deleteTask(@PathVariable String taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(NOT_FOUND, "Task not found");
        }
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

    @GetMapping("/dashboard-stats")
    public Map<String, Object> dashboardStats() {
        List<Task> tasks = taskMapper.selectList(null);
        List<Task> completed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).toList();
        double avgSimilarity = completed.stream().map(Task::getSimilarityScore).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0.0);
        double totalTokens = tasks.stream().mapToDouble(t -> nz(t.getInputTokens()) + nz(t.getOutputTokens())).sum();
        double totalDuration = tasks.stream().mapToDouble(t -> nz(t.getVideoADuration()) + nz(t.getVideoBDuration())).sum();
        double totalSize = tasks.stream().mapToDouble(t -> nz(t.getVideoASize()) + nz(t.getVideoBSize())).sum();

        // 分数为空表示“尚未评估”，而不是相似度为 0；分布统计应排除待处理和未完整执行的任务。
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

    @GetMapping("/prompt-templates/")
    public List<PromptTemplate> listPromptTemplates() {
        seedService.ensurePrompts();
        return promptMapper.selectList(new LambdaQueryWrapper<PromptTemplate>()
                .orderByDesc(PromptTemplate::getCreatedAt));
    }

    @PostMapping("/prompt-templates/")
    public PromptTemplate createPromptTemplate(@RequestBody PromptTemplateRequest body) {
        validatePromptRequest(body);
        PromptTemplate template = new PromptTemplate();
        template.setName(body.name().trim());
        template.setContent(body.content());
        promptMapper.insert(template);
        return template;
    }

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

    @DeleteMapping("/prompt-templates/{templateId}")
    public Map<String, String> deletePromptTemplate(@PathVariable String templateId) {
        if (promptMapper.selectById(templateId) == null) {
            throw new ResponseStatusException(NOT_FOUND, "Template not found");
        }
        promptMapper.deleteById(templateId);
        return Map.of("status", "ok");
    }

    @GetMapping("/models/")
    public List<AIModel> listModels() {
        seedService.ensureModels();
        return modelMapper.selectList(new LambdaQueryWrapper<AIModel>()
                .orderByAsc(AIModel::getSortOrder)
                .orderByDesc(AIModel::getCreatedAt));
    }

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

    @DeleteMapping("/models/{modelId}")
    public Map<String, String> deleteModel(@PathVariable String modelId) {
        if (modelMapper.selectById(modelId) == null) {
            throw new ResponseStatusException(NOT_FOUND, "Model not found");
        }
        modelMapper.deleteById(modelId);
        return Map.of("status", "ok");
    }

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

    @GetMapping("/model-logs/{id}")
    public ModelCallLog getModelLog(@PathVariable Long id) {
        ModelCallLog log = logMapper.selectById(id);
        if (log == null) {
            throw new ResponseStatusException(NOT_FOUND, "Model call log not found");
        }
        return log;
    }

    private JsonNode parsePreprocessOptions(String raw) {
        if (raw == null || raw.isBlank()) return NullNode.getInstance();
        try {
            JsonNode node = mapper.readTree(raw);
            if (!node.isObject()) throw new IllegalArgumentException("preprocess_options must be a JSON object");
            validateChoice(node, "recognition_mode", List.of("image", "video"));
            validateChoice(node, "sampling_type", List.of("fixed", "perceptual"));
            validateRange(node, "sampling_fps", 1, 5);
            validateRange(node, "resolution_val", 90, 2160);
            // Defaults preserve the bounded 0-15 second analysis contract for older clients.
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
        // 在 HTTP 边界校验 DTO，防止客户端直接写入不完整的 JPA 实体或仅供持久化使用的字段。
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
        // 文件存储与数据库无法共享同一事务，因此使用幂等补偿清理创建过程中的部分成功状态。
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
