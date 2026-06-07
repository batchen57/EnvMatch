package com.envmatch.service;

import com.envmatch.model.ModelCallLog;
import com.envmatch.repository.ModelCallLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiAnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiAnalysisService.class);
    private static final int COMPARISON_MAX_FRAMES = 8;
    private static final int COMPARISON_GRID_COLUMNS = 2;
    private static final int COMPARISON_FRAME_WIDTH = 640;
    private static final int COMPARISON_FRAME_HEIGHT = 360;
    private static final int COMPARISON_LABEL_HEIGHT = 32;
    private static final float COMPARISON_JPEG_QUALITY = 0.92f;
    private static final int QWEN_FRAME_FACTOR = 2;
    private static final int QWEN_IMAGE_FACTOR = 32;
    private static final int QWEN_VIDEO_MIN_PIXELS = 4 * 32 * 32;
    private static final int QWEN_VIDEO_MAX_PIXELS = 640 * 32 * 32;
    private static final double QWEN_FPS = 2.0;
    private static final int QWEN_FPS_MIN_FRAMES = 4;
    private static final int QWEN_FPS_MAX_FRAMES = 2000;
    private static final double QWEN_VIDEO_TOTAL_PIXELS = 131072.0 * 32 * 32;

    private static final String DEFAULT_PROMPT = """
            You are a professional video environment similarity analyst. Ignore people, foreground subjects,
            and actions. Focus only on comparing the background environment of the two materials.
            Return JSON only, without Markdown or explanatory text.
            """;

    private final ObjectMapper mapper;
    private final ModelCallLogRepository logRepository;
    private final HttpClient httpClient;
    private final Path storageDir;
    private final long maxInlineMediaBytes;

    public AiAnalysisService(ObjectMapper mapper,
                             ModelCallLogRepository logRepository,
                             @Value("${envmatch.storage-dir:storage}") String storageDir,
                             @Value("${envmatch.ai.max-inline-media-bytes:16777216}") long maxInlineMediaBytes) {
        this.mapper = mapper;
        this.logRepository = logRepository;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.maxInlineMediaBytes = Math.max(0, maxInlineMediaBytes);
    }

    public AiAnalysisResponse analyze(List<String> framesA,
                                      List<String> framesB,
                                      String prompt,
                                      String apiKey,
                                      String baseUrl,
                                      String modelId,
                                      String provider,
                                      String recognitionMode,
                                      String videoAPath,
                                      String videoBPath,
                                      String taskId,
                                      String taskName) {
        String effectivePrompt = buildPrompt(prompt);
        boolean gemini = isGemini(provider, modelId);
        if ((!gemini && (baseUrl == null || baseUrl.isBlank())) || apiKey == null || apiKey.isBlank()) {
            String message = "Model API key or endpoint URL is not configured";
            return new AiAnalysisResponse(errorResult(message), usageEstimate(effectivePrompt, framesA.size() + framesB.size(), 0), message);
        }

        LocalDateTime startedAt = LocalDateTime.now();
        String requestUrl = baseUrl == null ? "" : baseUrl;
        boolean nativeVlm = requestUrl.contains("/coding_plan/vlm");
        if (gemini) {
            requestUrl = geminiGenerateContentUrl(requestUrl, modelId, apiKey);
        } else if (!nativeVlm && !requestUrl.endsWith("/chat/completions")) {
            requestUrl = requestUrl.replaceAll("/+$", "") + "/chat/completions";
        }

        PayloadEnvelope envelope;
        try {
            envelope = buildPayload(framesA, framesB, effectivePrompt, modelId, provider, recognitionMode, nativeVlm, gemini, videoAPath, videoBPath);
        } catch (Exception e) {
            return new AiAnalysisResponse(errorResult(e.getMessage()), usageEstimate(effectivePrompt, 0, 0), e.getMessage());
        }
        ObjectNode payload = envelope.payload();
        ModelCallLog callLog = startLog(taskId, taskName, modelId, requestUrl, payload, startedAt);

        int status = 0;
        JsonNode responseBody = mapper.createObjectNode();
        String answer = "";
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
            if (!gemini) requestBuilder.header("Authorization", "Bearer " + apiKey);

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            responseBody = parseOrText(response.body());

            if (status != 200) {
                String message = "API returned error: " + status + " - " + truncate(response.body(), 500);
                finishLog(callLog, responseBody, LocalDateTime.now(), status, 0, 0);
                return new AiAnalysisResponse(errorResult(message), usageEstimate(effectivePrompt, framesA.size() + framesB.size(), 0), message);
            }

            answer = extractAnswer(responseBody, nativeVlm, gemini);
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("API returned empty content. Raw response: " + truncate(response.body(), 500));
            }
            JsonNode result = parseJsonResult(answer);
            String effectiveRecognitionMode = envelope.inlineVideo() ? "video" : "image";
            JsonNode usage = extractUsage(responseBody, effectivePrompt, answer, framesA.size() + framesB.size(), modelId,
                    effectiveRecognitionMode, videoAPath, videoBPath);
            finishLog(callLog, responseBody, LocalDateTime.now(), status,
                    usage.path("prompt_tokens").asDouble(0), usage.path("completion_tokens").asDouble(0));
            return new AiAnalysisResponse(result, usage, null);
        } catch (Exception e) {
            String message = "Model call failed: " + e.getMessage();
            ObjectNode body = mapper.createObjectNode();
            body.put("error", message);
            if (responseBody != null && !responseBody.isMissingNode() && !responseBody.isEmpty()) body.set("raw_response", responseBody);
            finishLog(callLog, body, LocalDateTime.now(), status == 200 ? 500 : (status == 0 ? 500 : status), 0, 0);
            return new AiAnalysisResponse(errorResult(message), usageEstimate(effectivePrompt, framesA.size() + framesB.size(), answer.length()), message);
        }
    }

    private String buildPrompt(String prompt) {
        String base = (prompt == null || prompt.isBlank()) ? DEFAULT_PROMPT : prompt;
        return base + """

                Compare video A and video B strictly for environmental similarity, and return JSON:
                {
                  "similarity_score": 85,
                  "dimension_scores": {
                    "lighting_weather": 80,
                    "architecture": 90,
                    "facilities": 70,
                    "vegetation": 85,
                    "road_surface": 100
                  },
                  "similar_points": ["similar point"],
                  "difference_points": ["difference point"],
                  "summary": "overall analysis"
                }
                """;
    }

    /**
     * 根据模型协议和识别模式构造请求载荷。
     *
     * <p>图片识别模式会为视频 A、B 分别生成一张 8 帧合图，再按不同协议作为两张图片
     * 放入 image_url、images 或 Gemini inlineData。仅支持单个 image_url 的 Native VLM
     * 会将两张合图上下合并后发送。视频识别模式优先内联原视频；视频不可读或总大小超过
     * 限制时，则降级为 A、B 各最多 5 张独立采样帧。</p>
     */
    private PayloadEnvelope buildPayload(List<String> framesA, List<String> framesB, String prompt, String modelId,
                                         String provider, String recognitionMode, boolean nativeVlm, boolean gemini,
                                         String videoAPath, String videoBPath) throws Exception {
        // 内联视频需要在内存中构造完整文件的 Base64；文件过大或不可读时，降级为数量受限的采样帧。
        boolean inlineVideo = "video".equalsIgnoreCase(recognitionMode) && canInlineMedia(videoAPath, videoBPath);
        if (gemini) {
            boolean requestedVideo = "video".equalsIgnoreCase(recognitionMode);
            return new PayloadEnvelope(buildGeminiPayload(framesA, framesB, prompt, requestedVideo, inlineVideo, videoAPath, videoBPath), inlineVideo);
        }

        ObjectNode payload = mapper.createObjectNode();
        if (nativeVlm) {
            payload.put("prompt", imageComparisonPrompt(prompt));
            payload.put("image_url", "data:image/jpeg;base64," + nativeComparisonGridBase64(framesA, framesB));
            return new PayloadEnvelope(payload, false);
        }

        payload.put("model", modelId);
        payload.put("temperature", 0.1);
        payload.put("max_tokens", 1024);
        ArrayNode messages = payload.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");

        boolean minimax = "MiniMax".equalsIgnoreCase(provider) || modelId.toLowerCase(Locale.ROOT).contains("minimax");
        if (minimax && !"video".equalsIgnoreCase(recognitionMode)) {
            user.put("content", imageComparisonPrompt(prompt));
            ArrayNode images = user.putArray("images");
            images.add(frameGridBase64(framesA, "A"));
            images.add(frameGridBase64(framesB, "B"));
            return new PayloadEnvelope(payload, false);
        }

        ArrayNode content = user.putArray("content");
        boolean qwenVideo = "video".equalsIgnoreCase(recognitionMode) && modelId.toLowerCase(Locale.ROOT).contains("qwen");
        if (qwenVideo && inlineVideo) {
            addVideo(content, videoAPath);
            addVideo(content, videoBPath);
            content.addObject().put("type", "text").put("text", prompt);
        } else if ("video".equalsIgnoreCase(recognitionMode)) {
            content.addObject().put("type", "text").put("text", prompt);
            for (String frame : framesA.stream().limit(5).toList()) addImage(content, frame);
            for (String frame : framesB.stream().limit(5).toList()) addImage(content, frame);
        } else {
            content.addObject().put("type", "text").put("text", imageComparisonPrompt(prompt));
            addBase64Image(content, frameGridBase64(framesA, "A"));
            addBase64Image(content, frameGridBase64(framesB, "B"));
        }
        return new PayloadEnvelope(payload, qwenVideo && inlineVideo);
    }

    private ObjectNode buildGeminiPayload(List<String> framesA, List<String> framesB, String prompt,
                                          boolean requestedVideo, boolean inlineVideo,
                                          String videoAPath, String videoBPath) throws Exception {
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode parts = payload.putArray("contents").addObject().put("role", "user").putArray("parts");
        if (inlineVideo) {
            addGeminiInlineData(parts, videoAPath, mimeType(videoAPath));
            addGeminiInlineData(parts, videoBPath, mimeType(videoBPath));
        } else if (requestedVideo) {
            for (String frame : framesA.stream().limit(5).toList()) addGeminiInlineData(parts, frame, mimeType(frame));
            for (String frame : framesB.stream().limit(5).toList()) addGeminiInlineData(parts, frame, mimeType(frame));
        } else {
            addGeminiBase64Image(parts, frameGridBase64(framesA, "A"));
            addGeminiBase64Image(parts, frameGridBase64(framesB, "B"));
        }
        parts.addObject().put("text", requestedVideo ? prompt : imageComparisonPrompt(prompt));
        ObjectNode generationConfig = payload.putObject("generationConfig");
        generationConfig.put("temperature", 0.1);
        generationConfig.put("maxOutputTokens", 1024);
        return payload;
    }

    private void addImage(ArrayNode content, String path) throws Exception {
        ObjectNode image = content.addObject();
        image.put("type", "image_url");
        image.putObject("image_url").put("url", "data:image/jpeg;base64," + fileBase64(path));
    }

    private void addBase64Image(ArrayNode content, String base64) {
        ObjectNode image = content.addObject();
        image.put("type", "image_url");
        image.putObject("image_url").put("url", "data:image/jpeg;base64," + base64);
    }

    private void addVideo(ArrayNode content, String path) throws Exception {
        Path resolved = resolvePath(path);
        if (path == null || path.isBlank() || !Files.exists(resolved)) throw new IllegalStateException("Qwen video mode requires readable video files");
        ObjectNode video = content.addObject();
        video.put("type", "video_url");
        video.putObject("video_url").put("url", "data:" + mimeType(path) + ";base64," + fileBase64(path));
        video.put("fps", 2);
    }

    private void addGeminiInlineData(ArrayNode parts, String path, String mimeType) throws Exception {
        Path resolved = resolvePath(path);
        if (path == null || path.isBlank() || !Files.exists(resolved)) throw new IllegalStateException("Gemini video mode requires readable video files");
        ObjectNode inlineData = parts.addObject().putObject("inlineData");
        inlineData.put("mimeType", mimeType);
        inlineData.put("data", fileBase64(path));
    }

    private void addGeminiBase64Image(ArrayNode parts, String base64) {
        ObjectNode inlineData = parts.addObject().putObject("inlineData");
        inlineData.put("mimeType", "image/jpeg");
        inlineData.put("data", base64);
    }

    private String imageComparisonPrompt(String prompt) {
        return """
                The first image grid contains frames from video A. The second image grid contains frames from video B.
                Frames in each grid are ordered from left to right and top to bottom, covering the clip from start to end.
                Compare corresponding time positions (A1 with B1, A2 with B2, and so on) while focusing on the environment.

                """ + prompt;
    }

    /**
     * 为单个视频生成供图片模型使用的 8 帧合图。
     *
     * <p>从完整采样结果中均匀选取最多 8 张有效帧，按从左到右、从上到下的顺序排列为
     * 两列四行，并标记为 A1-A8 或 B1-B8。单帧显示区域为 640x360，缩放时保持原始
     * 宽高比，剩余区域使用黑色留边，避免空间结构和物体形状失真。</p>
     *
     * <p>8 帧时输出尺寸为 {@code 1280 x 1568}。合成结果使用 0.92 质量编码为 JPEG，
     * 再转换为纯 Base64 字符串；Data URL 前缀由各模型协议的请求构造逻辑按需添加。</p>
     */
    String frameGridBase64(List<String> frames, String groupLabel) throws Exception {
        List<BufferedImage> images = evenlySample(frames, COMPARISON_MAX_FRAMES).stream()
                .map(this::readImage).filter(i -> i != null).toList();
        if (images.isEmpty()) throw new IllegalStateException("Unable to create frame grid; frame extraction produced no readable images");
        int columns = Math.min(COMPARISON_GRID_COLUMNS, images.size());
        int rows = (int) Math.ceil(images.size() / (double) columns);
        int rowHeight = COMPARISON_LABEL_HEIGHT + COMPARISON_FRAME_HEIGHT;
        BufferedImage grid = new BufferedImage(COMPARISON_FRAME_WIDTH * columns, rowHeight * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = grid.createGraphics();
        applyHighQualityRendering(g);
        for (int i = 0; i < images.size(); i++) {
            int x = (i % columns) * COMPARISON_FRAME_WIDTH;
            int y = (i / columns) * rowHeight;
            drawComparisonCell(g, images.get(i), x, y, groupLabel + (i + 1));
        }
        g.dispose();
        return encodeJpeg(grid);
    }

    private String nativeComparisonGridBase64(List<String> framesA, List<String> framesB) throws Exception {
        BufferedImage gridA = decodeBase64Image(frameGridBase64(framesA, "A"));
        BufferedImage gridB = decodeBase64Image(frameGridBase64(framesB, "B"));
        int width = Math.max(gridA.getWidth(), gridB.getWidth());
        BufferedImage combined = new BufferedImage(width, gridA.getHeight() + gridB.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, combined.getWidth(), combined.getHeight());
        g.drawImage(gridA, 0, 0, null);
        g.drawImage(gridB, 0, gridA.getHeight(), null);
        g.dispose();
        return encodeJpeg(combined);
    }

    private BufferedImage decodeBase64Image(String base64) throws Exception {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        if (image == null) throw new IllegalStateException("Unable to decode generated frame grid");
        return image;
    }

    private List<String> evenlySample(List<String> frames, int limit) {
        if (frames == null || frames.isEmpty() || limit <= 0) return List.of();
        if (frames.size() <= limit) return List.copyOf(frames);
        return java.util.stream.IntStream.range(0, limit)
                .mapToObj(i -> frames.get((int) Math.round(i * (frames.size() - 1.0) / (limit - 1))))
                .toList();
    }

    private void drawComparisonCell(Graphics2D g, BufferedImage source, int x, int y, String label) {
        g.setColor(new Color(32, 32, 32));
        g.fillRect(x, y, COMPARISON_FRAME_WIDTH, COMPARISON_LABEL_HEIGHT);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(label, x + 12, y + 23);

        int imageY = y + COMPARISON_LABEL_HEIGHT;
        g.setColor(Color.BLACK);
        g.fillRect(x, imageY, COMPARISON_FRAME_WIDTH, COMPARISON_FRAME_HEIGHT);
        double scale = Math.min(
                COMPARISON_FRAME_WIDTH / (double) source.getWidth(),
                COMPARISON_FRAME_HEIGHT / (double) source.getHeight()
        );
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int imageX = x + (COMPARISON_FRAME_WIDTH - width) / 2;
        int centeredY = imageY + (COMPARISON_FRAME_HEIGHT - height) / 2;
        g.drawImage(source, imageX, centeredY, width, height, null);
    }

    private void applyHighQualityRendering(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private String encodeJpeg(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(COMPARISON_JPEG_QUALITY);
        try (MemoryCacheImageOutputStream imageOut = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(imageOut);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private BufferedImage readImage(String path) {
        try {
            return ImageIO.read(resolvePath(path).toFile());
        } catch (Exception e) {
            return null;
        }
    }

    private String fileBase64(String path) throws Exception {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(resolvePath(path)));
    }

    boolean canInlineMedia(String... paths) {
        long total = 0L;
        for (String raw : paths) {
            Path path = resolvePath(raw);
            if (raw == null || raw.isBlank() || !Files.isRegularFile(path)) return false;
            try {
                total = Math.addExact(total, Files.size(path));
                if (total > maxInlineMediaBytes) {
                    LOGGER.info("Video payload exceeds inline limit ({} bytes); falling back to sampled frames", maxInlineMediaBytes);
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private String mimeType(String path) {
        if (path == null) return "video/mp4";
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".m4v")) return "video/x-m4v";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        return "video/mp4";
    }

    private JsonNode parseOrText(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            ObjectNode node = mapper.createObjectNode();
            node.put("raw", body);
            return node;
        }
    }

    private String extractAnswer(JsonNode body, boolean nativeVlm, boolean gemini) {
        if (gemini) {
            StringBuilder text = new StringBuilder();
            JsonNode parts = body.path("candidates").path(0).path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    String value = part.path("text").asText("");
                    if (!value.isBlank()) text.append(value);
                }
            }
            return text.toString();
        }
        if (nativeVlm && body.has("content")) {
            JsonNode content = body.path("content");
            if (content.isTextual()) return content.asText("");
            if (content.isObject() || content.isArray()) return content.toString();
        }
        JsonNode choices = body.path("choices");
        if (choices.isArray() && !choices.isEmpty()) return choices.get(0).path("message").path("content").asText("");
        return body.path("output").path("text").asText(body.path("result").asText(""));
    }

    private JsonNode parseJsonResult(String answer) throws Exception {
        String text = answer == null ? "" : answer.trim();
        Matcher fencedMatcher = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE).matcher(text);
        if (fencedMatcher.find()) text = fencedMatcher.group(1).trim();
        if (!text.startsWith("{")) {
            Matcher objectMatcher = Pattern.compile("\\{[\\s\\S]*}").matcher(text);
            if (objectMatcher.find()) text = objectMatcher.group();
        }
        JsonNode raw;
        try {
            raw = mapper.readTree(text);
        } catch (Exception e) {
            raw = mapper.readTree(text.replaceAll(",\\s*([}\\]])", "$1"));
        }
        if (raw == null || raw.isMissingNode() || !raw.isObject()) throw new IllegalArgumentException("API returned a non-object JSON result: " + truncate(text, 300));
        ObjectNode normalized = ((ObjectNode) raw).deepCopy();
        JsonNode d = raw.path("dimension_scores");
        ObjectNode dims = mapper.createObjectNode();
        dims.put("lighting_weather", number(d, "lighting_weather", "光照天气", "天气光线"));
        dims.put("architecture", number(d, "architecture", "建筑风格"));
        dims.put("facilities", number(d, "facilities", "固定设施"));
        dims.put("vegetation", number(d, "vegetation", "植被绿化", "地貌植被"));
        dims.put("road_surface", number(d, "road_surface", "地面材质"));
        normalized.set("dimension_scores", dims);
        if (!normalized.has("similar_points")) normalized.set("similar_points", mapper.createArrayNode());
        if (!normalized.has("difference_points")) normalized.set("difference_points", mapper.createArrayNode());
        if (!normalized.has("summary")) normalized.put("summary", "");
        if (!normalized.has("similarity_score")) normalized.put("similarity_score", 0);
        return normalized;
    }

    private int number(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isNumber()) return Math.max(0, Math.min(100, value.asInt(0)));
            if (value.isTextual()) {
                try {
                    return Math.max(0, Math.min(100, Integer.parseInt(value.asText().trim())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    private JsonNode extractUsage(JsonNode body, String prompt, String answer, int imageCount, String modelId,
                                  String recognitionMode, String videoAPath, String videoBPath) {
        JsonNode usage = body.path("usage");
        ObjectNode out = mapper.createObjectNode();
        int promptTokens = usage.path("prompt_tokens").asInt(usage.path("input_tokens").asInt(0));
        int completionTokens = usage.path("completion_tokens").asInt(usage.path("output_tokens").asInt(0));
        if (promptTokens <= 0) promptTokens = body.path("usageMetadata").path("promptTokenCount").asInt(0);
        if (completionTokens <= 0) completionTokens = body.path("usageMetadata").path("candidatesTokenCount").asInt(0);
        if (promptTokens <= 0) {
            boolean qwenVideo = "video".equalsIgnoreCase(recognitionMode) && modelId != null && modelId.toLowerCase(Locale.ROOT).contains("qwen");
            if (qwenVideo) promptTokens = qwenVideoTokens(videoAPath) + qwenVideoTokens(videoBPath) + prompt.length() / 2 + 150;
            else promptTokens = Math.max(1, prompt.length() / 2 + imageCount * 300);
        }
        if (completionTokens <= 0) completionTokens = Math.max(1, (answer == null ? 0 : answer.length()) / 2);
        out.put("prompt_tokens", promptTokens);
        out.put("completion_tokens", completionTokens);
        out.put("total_tokens", promptTokens + completionTokens);
        if (usage.isMissingNode() || usage.isNull() || usage.isEmpty()) out.put("estimated", true);
        return out;
    }

    private JsonNode usageEstimate(String prompt, int imageCount, int answerLen) {
        ObjectNode out = mapper.createObjectNode();
        int promptTokens = Math.max(1, prompt.length() / 2 + imageCount * 300);
        int completionTokens = Math.max(1, answerLen / 2);
        out.put("prompt_tokens", promptTokens);
        out.put("completion_tokens", completionTokens);
        out.put("total_tokens", promptTokens + completionTokens);
        out.put("estimated", true);
        return out;
    }

    private boolean isGemini(String provider, String modelId) {
        String providerValue = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        String modelValue = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
        return providerValue.contains("google") || modelValue.contains("gemini");
    }

    private String geminiGenerateContentUrl(String baseUrl, String modelId, String apiKey) {
        String base = (baseUrl == null || baseUrl.isBlank()) ? "https://generativelanguage.googleapis.com/v1beta" : baseUrl.replaceAll("/+$", "");
        if (base.contains(":generateContent")) return base.contains("?") ? base : base + "?key=" + apiKey;
        String modelPath = modelId != null && modelId.startsWith("models/") ? modelId : "models/" + modelId;
        return base + "/" + modelPath + ":generateContent?key=" + apiKey;
    }

    private int qwenVideoTokens(String videoPath) {
        VideoProbe probe = probeVideo(videoPath);
        if (probe == null || probe.width() <= 0 || probe.height() <= 0 || probe.totalFrames() <= 0 || probe.fps() <= 0) return 0;
        int nframes = smartNframes(QWEN_FPS, probe.totalFrames(), probe.fps());
        if (nframes <= 0) return 0;
        double maxPixels = Math.max(
                Math.min(QWEN_VIDEO_MAX_PIXELS, QWEN_VIDEO_TOTAL_PIXELS / nframes * QWEN_FRAME_FACTOR),
                (int) (QWEN_VIDEO_MIN_PIXELS * 1.05)
        );
        int hBar = Math.max(QWEN_IMAGE_FACTOR, roundByFactor(probe.height(), QWEN_IMAGE_FACTOR));
        int wBar = Math.max(QWEN_IMAGE_FACTOR, roundByFactor(probe.width(), QWEN_IMAGE_FACTOR));
        if (hBar * wBar > maxPixels) {
            double beta = Math.sqrt((probe.height() * probe.width()) / maxPixels);
            hBar = floorByFactor(probe.height() / beta, QWEN_IMAGE_FACTOR);
            wBar = floorByFactor(probe.width() / beta, QWEN_IMAGE_FACTOR);
        } else if (hBar * wBar < QWEN_VIDEO_MIN_PIXELS) {
            double beta = Math.sqrt(QWEN_VIDEO_MIN_PIXELS / (double) (probe.height() * probe.width()));
            hBar = ceilByFactor(probe.height() * beta, QWEN_IMAGE_FACTOR);
            wBar = ceilByFactor(probe.width() * beta, QWEN_IMAGE_FACTOR);
        }
        return (int) (Math.ceil(nframes / 2.0) * (hBar / 32.0) * (wBar / 32.0)) + 2;
    }

    private int smartNframes(double fpsParam, int totalFrames, double videoFps) {
        double fps = fpsParam > 0 ? fpsParam : QWEN_FPS;
        int minFrames = ceilByFactor(QWEN_FPS_MIN_FRAMES, QWEN_FRAME_FACTOR);
        int maxFrames = floorByFactor(Math.min(QWEN_FPS_MAX_FRAMES, totalFrames), QWEN_FRAME_FACTOR);
        double duration = videoFps == 0 ? 0 : totalFrames / videoFps;
        double nframes = videoFps == 0 ? 0 : Math.ceil(duration * videoFps) / videoFps * fps;
        return (int) Math.min(Math.min(Math.max(nframes, minFrames), maxFrames), totalFrames);
    }

    private int roundByFactor(double number, int factor) {
        return (int) Math.round(number / factor) * factor;
    }

    private int ceilByFactor(double number, int factor) {
        return (int) Math.ceil(number / factor) * factor;
    }

    private int floorByFactor(double number, int factor) {
        return (int) Math.floor(number / factor) * factor;
    }

    private VideoProbe probeVideo(String videoPath) {
        if (videoPath == null || videoPath.isBlank() || !Files.exists(resolvePath(videoPath))) return null;
        try {
            Process process = new ProcessBuilder("ffprobe", "-v", "quiet", "-print_format", "json",
                    "-show_format", "-show_streams", resolvePath(videoPath).toString()).redirectErrorStream(true).start();
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            String stdout = new String(process.getInputStream().readAllBytes());
            JsonNode root = mapper.readTree(stdout);
            JsonNode video = null;
            for (JsonNode stream : root.path("streams")) {
                if ("video".equals(stream.path("codec_type").asText())) {
                    video = stream;
                    break;
                }
            }
            if (video == null) return null;
            int width = video.path("width").asInt(0);
            int height = video.path("height").asInt(0);
            double fps = parseFps(video.path("avg_frame_rate").asText("0"));
            double duration = root.path("format").path("duration").asDouble(0.0);
            int frames = video.path("nb_frames").asInt(0);
            if (frames <= 0 && duration > 0 && fps > 0) frames = (int) Math.ceil(duration * fps);
            return new VideoProbe(width, height, frames, fps);
        } catch (Exception e) {
            return null;
        }
    }

    private double parseFps(String raw) {
        try {
            if (raw.contains("/")) {
                String[] parts = raw.split("/");
                double numerator = Double.parseDouble(parts[0]);
                double denominator = Double.parseDouble(parts[1]);
                return denominator == 0 ? 0.0 : numerator / denominator;
            }
            return Double.parseDouble(raw);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private JsonNode errorResult(String message) {
        ObjectNode result = mapper.createObjectNode();
        result.put("similarity_score", 0);
        ObjectNode dims = result.putObject("dimension_scores");
        dims.put("lighting_weather", 0);
        dims.put("architecture", 0);
        dims.put("facilities", 0);
        dims.put("vegetation", 0);
        dims.put("road_surface", 0);
        result.set("similar_points", mapper.createArrayNode());
        result.set("difference_points", mapper.createArrayNode());
        result.put("summary", "Analysis failed: " + message);
        return result;
    }

    private ModelCallLog startLog(String taskId, String taskName, String modelId, String modelUrl,
                                  JsonNode requestPayload, LocalDateTime startedAt) {
        try {
            ModelCallLog log = new ModelCallLog();
            log.setTaskId(taskId);
            log.setTaskName(taskName);
            log.setModelId(modelId);
            // 保留审计所需的元数据，但禁止将访问凭证和二进制载荷持久化到 SQLite。
            log.setModelUrl(redactUrl(modelUrl));
            log.setRequestPayload(sanitizeForLog(requestPayload));
            log.setStartedAt(startedAt);
            log.setStatusCode("PROCESSING");
            return logRepository.saveAndFlush(log);
        } catch (Exception e) {
            LOGGER.warn("Failed to start model call log for task {}", taskId, e);
            return null;
        }
    }

    private void finishLog(ModelCallLog log, JsonNode responseBody, LocalDateTime endedAt, int statusCode,
                           double inputTokens, double outputTokens) {
        if (log == null) return;
        try {
            log.setResponseBody(sanitizeForLog(responseBody));
            log.setEndedAt(endedAt);
            log.setStatusCode(String.valueOf(statusCode));
            log.setInputTokens(inputTokens);
            log.setOutputTokens(outputTokens);
            logRepository.save(log);
        } catch (Exception e) {
            LOGGER.warn("Failed to finish model call log for task {}", log.getTaskId(), e);
        }
    }

    private Path resolvePath(String raw) {
        if (raw == null || raw.isBlank()) return Path.of("");
        Path path = Path.of(raw);
        if (Files.exists(path)) return path;
        String normalized = raw.replace("\\", "/");
        String storageName = storageDir.getFileName() == null ? "storage" : storageDir.getFileName().toString();
        String prefix = storageName + "/";
        if (normalized.startsWith(prefix)) {
            Path fromStorage = storageDir.resolve(normalized.substring(prefix.length())).normalize();
            if (Files.exists(fromStorage)) return fromStorage;
        }
        return path;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    JsonNode sanitizeForLog(JsonNode source) {
        // 基于深拷贝清理审计数据，避免脱敏过程修改实际发送给模型的请求载荷。
        return sanitizeForLog(source, null);
    }

    private JsonNode sanitizeForLog(JsonNode source, String parentMimeType) {
        if (source == null || source.isNull()) return source;
        if (source.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            String mimeType = source.path("mimeType").asText(parentMimeType);
            source.fields().forEachRemaining(entry ->
                    copy.set(entry.getKey(), sanitizeField(entry.getKey(), entry.getValue(), mimeType)));
            return copy;
        }
        if (source.isArray()) {
            ArrayNode copy = mapper.createArrayNode();
            source.forEach(value -> copy.add(sanitizeForLog(value, parentMimeType)));
            return copy;
        }
        if (source.isTextual()) return sanitizedText(source.asText(), parentMimeType);
        return source.deepCopy();
    }

    private JsonNode sanitizeField(String key, JsonNode value, String mimeType) {
        if (value.isTextual() && "data".equals(key) && mimeType != null && mimeType.startsWith("video/")) {
            return mapper.getNodeFactory().textNode(redactedMarker("video", value.asText()));
        }
        return sanitizeForLog(value, mimeType);
    }

    private JsonNode sanitizedText(String value, String mimeType) {
        if (value.startsWith("data:video/")) return mapper.getNodeFactory().textNode(redactedMarker("video", value));
        if (mimeType != null && mimeType.startsWith("video/")) {
            return mapper.getNodeFactory().textNode(redactedMarker("video", value));
        }
        if (looksLikeBase64(value) && value.length() > 1_000_000) {
            return mapper.getNodeFactory().textNode(redactedMarker("large-base64", value));
        }
        return mapper.getNodeFactory().textNode(value);
    }

    private boolean looksLikeBase64(String value) {
        String candidate = value;
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma >= 0) candidate = value.substring(comma + 1);
        if (candidate.length() < 256) return false;
        int sampleLength = Math.min(candidate.length(), 512);
        return candidate.substring(0, sampleLength).matches("[A-Za-z0-9+/=\\r\\n]+");
    }

    private String redactedMarker(String type, String value) {
        return "[redacted " + type + " payload, encoded_chars=" + value.length() + "]";
    }

    private String redactUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("([?&]key=)[^&]+", "$1[redacted]");
    }

    private record VideoProbe(int width, int height, int totalFrames, double fps) {
    }

    private record PayloadEnvelope(ObjectNode payload, boolean inlineVideo) {
    }
}
