package com.envmatch.service;

import com.envmatch.model.ModelCallLog;
import com.envmatch.mapper.ModelCallLogMapper;
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

/**
 * AI 多模态大模型分析核心服务类。
 * 
 * <p>该类主要负责将提取出来的关键帧或裁剪后的短视频包装成符合各大模型协议的 Payload，
 * 并通过 HTTP 客户端发送至对应的 API 端点。它包含了以下核心模块：
 * <ul>
 *   <li><b>多模型协议适配</b>：支持 Google Gemini 专属格式、OpenAI 兼容的多模态 chat/completions 格式、MiniMax 特有的原生 VLM（视频/图片）格式、以及通义千问（Qwen-VL）专有格式。</li>
 *   <li><b>图像网格合成（Image Grid）</b>：如果是图片模式，将多张关键帧拼接成一张大图（8 帧合图，以 640x360 黑色填充保持原宽高比），从而节省大模型的输入图片张数并大幅节省输入 Token 成本。</li>
 *   <li><b>Qwen 视频 Token 精确估算</b>：实现了阿里云官方通义千问 Qwen2-VL 视频 Token 的计算算法公式。</li>
 *   <li><b>审计日志脱敏（Sanitization）</b>：对发送给模型的 Payload 和响应进行深拷贝脱敏，防止将用户视频 Base64 大字段和 API Key 写入 PostgreSQL 数据库审计日志中，避免造成数据库膨胀和隐私泄露。</li>
 * </ul>
 * </p>
 */
@Service
public class AiAnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiAnalysisService.class);
    
    /** 合图支持的最大抽帧数量 */
    private static final int COMPARISON_MAX_FRAMES = 8;
    
    /** 图像网格合图的列数 */
    private static final int COMPARISON_GRID_COLUMNS = 2;
    
    /** 拼接图中单帧的缩放宽度 */
    private static final int COMPARISON_FRAME_WIDTH = 640;
    
    /** 拼接图中单帧的缩放高度 */
    private static final int COMPARISON_FRAME_HEIGHT = 360;
    
    /** 拼接图中单帧标签文字区域的高度 */
    private static final int COMPARISON_LABEL_HEIGHT = 32;
    
    /** 拼接图最终 JPEG 压缩画质 */
    private static final float COMPARISON_JPEG_QUALITY = 0.92f;
    
    // Qwen2-VL 视频 Token 算力公式常量（与官方算法严格对齐）
    private static final int QWEN_FRAME_FACTOR = 2;
    private static final int QWEN_IMAGE_FACTOR = 32;
    private static final int QWEN_VIDEO_MIN_PIXELS = 4 * 32 * 32;
    private static final int QWEN_VIDEO_MAX_PIXELS = 640 * 32 * 32;
    private static final double QWEN_FPS = 2.0;
    private static final int QWEN_FPS_MIN_FRAMES = 4;
    private static final int QWEN_FPS_MAX_FRAMES = 2000;
    private static final double QWEN_VIDEO_TOTAL_PIXELS = 131072.0 * 32 * 32;

    /** 默认的对比系统提示词 */
    private static final String DEFAULT_PROMPT = """
            你是一名反欺诈调查中的环境鉴定专家。你的核心任务是判断两段视频/关键帧是否拍摄于同一个物理空间（如同一间房屋、门店、办公室），
            以识别中介团伙反复使用同一场地进行虚假申请的行为。
 
            【分析原则】
            1. 完全忽略画面中的人物、手持物品、手机/电脑屏幕内容、临时摆放的文件等前景干扰。
            2. 聚焦不可移动或难以短期更改的固定环境特征。
            3. 重点关注：墙面颜色/纹理/壁纸、地板/地砖材质与纹理、门窗框架与把手样式、天花板与灯具、
               开关面板/插座位置、固定家具（橱柜/嵌入式衣柜）、空间布局与房间结构。
            4. 如果两个环境高度相似（得分≥80），请在 similar_points 中列出具体匹配的固定特征作为证据。
 
            仅返回严格 JSON，不要包含 Markdown 代码块标记或任何解释性文字。
            """;

    /** JSON 对象解析与映射器 */
    private final ObjectMapper mapper;
    
    /** 模型审计日志数据库 Mapper */
    private final ModelCallLogMapper logMapper;
    
    /** 执行模型 API HTTP 请求的客户端 */
    private final HttpClient httpClient;
    
    /** 标准化存储根目录路径 */
    private final Path storageDir;
    
    /** 允许内联 Base64 视频流的最大字节限制 */
    private final long maxInlineMediaBytes;

    /**
     * 构造函数，初始化存储目录及内联大小参数限制。
     */
    public AiAnalysisService(ObjectMapper mapper,
                             ModelCallLogMapper logMapper,
                             @Value("${envmatch.storage-dir:storage}") String storageDir,
                             @Value("${envmatch.ai.max-inline-media-bytes:16777216}") long maxInlineMediaBytes) {
        this.mapper = mapper;
        this.logMapper = logMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.maxInlineMediaBytes = Math.max(0, maxInlineMediaBytes);
    }

    /**
     * 发起大模型接口调用，并分析返回两组素材的相似度结果。
     *
     * @param framesA          素材 A 的抽帧路径列表
     * @param framesB          素材 B 的抽帧路径列表
     * @param prompt           用户自定义提示词
     * @param apiKey           API 访问 Key
     * @param baseUrl          API 基础端点 URL
     * @param modelId          模型技术标识名称
     * @param provider         模型厂商标识名称
     * @param recognitionMode  识别模式：image（帧拼接合图）或 video（内联原视频）
     * @param videoAPath       素材 A 视频存储路径
     * @param videoBPath       素材 B 视频存储路径
     * @param taskId           关联任务 ID
     * @param taskName         关联任务名称
     * @return 封装的 {@link AiAnalysisResponse} 响应对象
     */
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
        
        // 验证必要的连接密钥与地址参数
        if ((!gemini && (baseUrl == null || baseUrl.isBlank())) || apiKey == null || apiKey.isBlank()) {
            String message = "Model API key or endpoint URL is not configured";
            return new AiAnalysisResponse(errorResult(message), usageEstimate(effectivePrompt, framesA.size() + framesB.size(), 0), message);
        }

        LocalDateTime startedAt = LocalDateTime.now();
        String requestUrl = baseUrl == null ? "" : baseUrl;
        
        // 针对 MiniMax Native VLM 端点的兼容映射
        if ("MiniMax-M2.7".equalsIgnoreCase(modelId) && "https://api.minimaxi.com/v1".equals(requestUrl.replaceAll("/+$", ""))) {
            requestUrl = "https://api.minimaxi.com/v1/coding_plan/vlm";
        }
        boolean nativeVlm = requestUrl.contains("/coding_plan/vlm");
        
        // 构建最终的请求网络地址
        if (gemini) {
            requestUrl = geminiGenerateContentUrl(requestUrl, modelId, apiKey);
        } else if (!nativeVlm && !requestUrl.endsWith("/chat/completions")) {
            requestUrl = requestUrl.replaceAll("/+$", "") + "/chat/completions";
        }

        PayloadEnvelope envelope;
        try {
            // 构造请求的 JSON Body 载荷，判断是否允许使用 inlineVideo
            envelope = buildPayload(framesA, framesB, effectivePrompt, modelId, provider, recognitionMode, nativeVlm, gemini, videoAPath, videoBPath);
        } catch (Exception e) {
            return new AiAnalysisResponse(errorResult(e.getMessage()), usageEstimate(effectivePrompt, 0, 0), e.getMessage());
        }
        
        ObjectNode payload = envelope.payload();
        // 记录模型调用起始审计日志（已进行深拷贝脱敏，去除敏感密钥和二进制大字段）
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

            // 发送 HTTP POST 请求
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            responseBody = parseOrText(response.body());

            if (status != 200) {
                String message = "API returned error: " + status + " - " + truncate(response.body(), 500);
                finishLog(callLog, responseBody, LocalDateTime.now(), status, 0, 0);
                return new AiAnalysisResponse(errorResult(message), usageEstimate(effectivePrompt, framesA.size() + framesB.size(), 0), message);
            }

            // 提取模型响应的文本答案
            answer = extractAnswer(responseBody, nativeVlm, gemini);
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("API returned empty content. Raw response: " + truncate(response.body(), 500));
            }
            
            // 解析大模型返回的文本为结构化的 JSON 对比结果对象
            JsonNode result = parseJsonResult(answer);
            String effectiveRecognitionMode = envelope.inlineVideo() ? "video" : "image";
            
            // 提取/估算本次模型调用的 Token 消耗用量
            JsonNode usage = extractUsage(responseBody, effectivePrompt, answer, framesA.size() + framesB.size(), modelId,
                    effectiveRecognitionMode, videoAPath, videoBPath);
            
            // 写入结束审计日志
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

    /**
     * 将用户 Prompt 与要求的输出 JSON 数据格式进行拼接，生成最终的指导提示词。
     */
    private String buildPrompt(String prompt) {
        String base = (prompt == null || prompt.isBlank()) ? DEFAULT_PROMPT : prompt;
        return base + """
 
                严格对比素材 A 和素材 B 的拍摄环境，判断是否为同一物理空间。返回如下 JSON 结构：
                {
                  "similarity_score": 85,
                  "dimension_scores": {
                    "indoor_layout": 80,
                    "wall_floor_material": 90,
                    "furniture_fixtures": 70,
                    "window_door_style": 85,
                    "lighting_environment": 75
                  },
                  "similar_points": ["两段视频中均可见相同花纹的米色壁纸和白色踢脚线"],
                  "difference_points": ["视频A中桌面有一台笔记本电脑，视频B中没有（属前景差异，不影响环境判断）"],
                  "summary": "综合分析：两段视频大概率拍摄于同一室内空间，墙面装饰、地板材质和空间结构高度一致。"
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
        // MiniMax 专属的原生 VLM 协议适配（只支持一个大图参数）
        if (nativeVlm) {
            payload.put("prompt", imageComparisonPrompt(prompt));
            payload.put("image_url", "data:image/jpeg;base64," + nativeComparisonGridBase64(framesA, framesB));
            return new PayloadEnvelope(payload, false);
        }

        payload.put("model", modelId);
        payload.put("temperature", 0.1);
        payload.put("max_tokens", "MiniMax".equalsIgnoreCase(provider) ? 4096 : 1024);
        ArrayNode messages = payload.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");

        // 阿里大模型兼容格式
        boolean isMiniMax = ("MiniMax".equalsIgnoreCase(provider) || (modelId != null && modelId.toLowerCase(Locale.ROOT).contains("minimax")))
                && (modelId == null || !modelId.toLowerCase(Locale.ROOT).contains("-m3"));
        if (isMiniMax) {
            user.put("content", imageComparisonPrompt(prompt));
            ArrayNode images = user.putArray("images");
            images.add(nativeComparisonGridBase64(framesA, framesB));
            return new PayloadEnvelope(payload, false);
        }

        ArrayNode content = user.putArray("content");
        boolean openAiVideo = "video".equalsIgnoreCase(recognitionMode)
                && (modelId.toLowerCase(Locale.ROOT).contains("qwen") || modelId.toLowerCase(Locale.ROOT).contains("minimax"));
        if (openAiVideo && inlineVideo) {
            // OpenAI 兼容视频模式：内联素材原视频
            addVideo(content, videoAPath, modelId);
            addVideo(content, videoBPath, modelId);
            content.addObject().put("type", "text").put("text", prompt);
        } else if ("video".equalsIgnoreCase(recognitionMode)) {
            // 超出大小限制降级为多张采样帧形式发送
            content.addObject().put("type", "text").put("text", prompt);
            for (String frame : framesA.stream().limit(5).toList()) addImage(content, frame);
            for (String frame : framesB.stream().limit(5).toList()) addImage(content, frame);
        } else {
            // 默认的图像模式：将素材 A/B 分别转换为一张拼接拼接网格图（双大图模式）
            content.addObject().put("type", "text").put("text", imageComparisonPrompt(prompt));
            addBase64Image(content, frameGridBase64(framesA, "A"));
            addBase64Image(content, frameGridBase64(framesB, "B"));
        }
        return new PayloadEnvelope(payload, openAiVideo && inlineVideo);
    }

    /**
     * 构建 Google Gemini 专门的 Contents / Parts 多模态调用协议。
     */
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

    /**
     * 添加图片到请求 Payload (OpenAI 格式)
     */
    private void addImage(ArrayNode content, String path) throws Exception {
        ObjectNode image = content.addObject();
        image.put("type", "image_url");
        image.putObject("image_url").put("url", "data:image/jpeg;base64," + fileBase64(path));
    }

    /**
     * 添加 Base64 图片到请求 Payload (OpenAI 格式)
     */
    private void addBase64Image(ArrayNode content, String base64) {
        ObjectNode image = content.addObject();
        image.put("type", "image_url");
        image.putObject("image_url").put("url", "data:image/jpeg;base64," + base64);
    }

    /**
     * 添加内联视频到请求 Payload (OpenAI 格式，例如通义千问, MiniMax-M3)
     */
    private void addVideo(ArrayNode content, String path, String modelId) throws Exception {
        Path resolved = resolvePath(path);
        if (path == null || path.isBlank() || !Files.exists(resolved)) throw new IllegalStateException("Video mode requires readable video files");
        ObjectNode video = content.addObject();
        video.put("type", "video_url");
        ObjectNode videoUrl = video.putObject("video_url");
        videoUrl.put("url", "data:" + mimeType(path) + ";base64," + fileBase64(path));
        
        boolean isMiniMax = modelId != null && modelId.toLowerCase(Locale.ROOT).contains("minimax");
        if (isMiniMax) {
            videoUrl.put("fps", 1.0); // MiniMax-M3 视频 fps 参数放在 video_url 内部
        } else {
            video.put("fps", 2);      // Qwen-VL 视频 fps 参数放在 content 根节点上
        }
    }

    /**
     * 添加内联多媒体到 Gemini Payload
     */
    private void addGeminiInlineData(ArrayNode parts, String path, String mimeType) throws Exception {
        Path resolved = resolvePath(path);
        if (path == null || path.isBlank() || !Files.exists(resolved)) throw new IllegalStateException("Gemini video mode requires readable video files");
        ObjectNode inlineData = parts.addObject().putObject("inlineData");
        inlineData.put("mimeType", mimeType);
        inlineData.put("data", fileBase64(path));
    }

    /**
     * 添加 Base64 图像到 Gemini Payload
     */
    private void addGeminiBase64Image(ArrayNode parts, String base64) {
        ObjectNode inlineData = parts.addObject().putObject("inlineData");
        inlineData.put("mimeType", "image/jpeg");
        inlineData.put("data", base64);
    }

    /**
     * 拼接网格合图的辅助 Prompt 头。
     */
    private String imageComparisonPrompt(String prompt) {
        return """
                第一张合图包含素材 A 的关键帧，第二张合图包含素材 B 的关键帧。
                帧按从左到右、从上到下排列，覆盖视频片段的时间线。
                请逐一对比对应时间位置的帧（A1 对 B1、A2 对 B2，依此类推），
                聚焦背景环境中的固定特征（墙面、地面、门窗、灯具、家具布局），
                忽略人物和可移动物品，判断两组帧是否拍摄于同一物理空间。
 
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
            // 绘制网格中的每一帧，包括其序号标签（如 A1, A2...）
            drawComparisonCell(g, images.get(i), x, y, groupLabel + (i + 1));
        }
        g.dispose();
        return encodeJpeg(grid);
    }

    /**
     * 用于 MiniMax 等原生只支持单张输入图像的模型。
     * 将 A 素材网格合图和 B 素材网格合图纵向拼接成一张包含所有抽帧画面的巨大拼图。
     */
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

    /**
     * 将 Base64 字符串解码还原为 Java BufferedImage 对象。
     */
    private BufferedImage decodeBase64Image(String base64) throws Exception {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        if (image == null) throw new IllegalStateException("Unable to decode generated frame grid");
        return image;
    }

    /**
     * 均匀取样抽稀算法，将视频的所有抽帧路径列表缩减至限制值。
     */
    private List<String> evenlySample(List<String> frames, int limit) {
        if (frames == null || frames.isEmpty() || limit <= 0) return List.of();
        if (frames.size() <= limit) return List.copyOf(frames);
        return java.util.stream.IntStream.range(0, limit)
                .mapToObj(i -> frames.get((int) Math.round(i * (frames.size() - 1.0) / (limit - 1))))
                .toList();
    }

    /**
     * 在拼接图的指定 X/Y 偏移位置绘制单个关键帧单元。
     * 包含序号文字顶栏（黑色背景、白色粗体）和图片展示区域（不足 640x360 时以黑色留边填充）。
     */
    private void drawComparisonCell(Graphics2D g, BufferedImage source, int x, int y, String label) {
        // 1. 绘制顶栏标签框及文字
        g.setColor(new Color(32, 32, 32));
        g.fillRect(x, y, COMPARISON_FRAME_WIDTH, COMPARISON_LABEL_HEIGHT);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(label, x + 12, y + 23);
 
        // 2. 绘制图片放置区
        int imageY = y + COMPARISON_LABEL_HEIGHT;
        g.setColor(Color.BLACK);
        g.fillRect(x, imageY, COMPARISON_FRAME_WIDTH, COMPARISON_FRAME_HEIGHT);
        
        // 计算等比例缩放大小，防止环境结构变形
        double scale = Math.min(
                COMPARISON_FRAME_WIDTH / (double) source.getWidth(),
                COMPARISON_FRAME_HEIGHT / (double) source.getHeight()
        );
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        
        // 居中对齐坐标
        int imageX = x + (COMPARISON_FRAME_WIDTH - width) / 2;
        int centeredY = imageY + (COMPARISON_FRAME_HEIGHT - height) / 2;
        g.drawImage(source, imageX, centeredY, width, height, null);
    }

    /**
     * 启用 Graphics2D 双立方差值及抗锯齿渲染，避免缩放图严重失真。
     */
    private void applyHighQualityRendering(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * 将内存图片编码压缩为 JPEG 字节流并转化为 Base64 字符串。
     */
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

    /**
     * 读取指定相对/绝对物理路径图片文件为 BufferedImage。
     */
    private BufferedImage readImage(String path) {
        try {
            return ImageIO.read(resolvePath(path).toFile());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读取文件字节码并转换为 Base64。
     */
    private String fileBase64(String path) throws Exception {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(resolvePath(path)));
    }

    /**
     * 判断待上传的视频/多媒体文件大小是否适合作为 Base64 内联参数传输。
     * 如果两份视频总大小超过配置上限（如 16MB），则自动判定为 false，以防触发 JVM 内存溢出或模型网关拒绝。
     */
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

    /**
     * 推算多媒体文件的 MimeType。
     */
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

    /**
     * 解析模型返回的 Response Body。如果非标准 JSON，将其包裹在文本属性中。
     */
    private JsonNode parseOrText(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            ObjectNode node = mapper.createObjectNode();
            node.put("raw", body);
            return node;
        }
    }

    /**
     * 针对不同厂商响应协议结构，提取具体的文本答案。
     */
    private String extractAnswer(JsonNode body, boolean nativeVlm, boolean gemini) {
        // 1. Google Gemini 响应结构解析
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
        
        // 2. MiniMax 原生 VLM 响应结构解析
        if (nativeVlm && body.has("content")) {
            JsonNode content = body.path("content");
            if (content.isTextual()) return content.asText("");
            if (content.isObject() || content.isArray()) return content.toString();
        }
        
        // 3. OpenAI 兼容协议响应结构解析
        JsonNode choices = body.path("choices");
        if (choices.isArray() && !choices.isEmpty()) return choices.get(0).path("message").path("content").asText("");
        
        // 保底解析
        return body.path("output").path("text").asText(body.path("result").asText(""));
    }

    /**
     * 关键结果解析器。
     * <p>由于大模型可能在输出中附带 ```json ... ``` 的 Markdown 标记，或者在生成中夹带
     * 思考过程标签（如 DeepSeek R1 产出的 {@code <think>...</think>}），
     * 该方法使用正则表达式对这些非 JSON 字符进行清理，确保能够百分百成功进行 JSON 反序列化。
     * 解析后，还会对其各评分维度（dimension_scores）和字段进行补正和归一化处理，防止数据库缺失关键列。</p>
     */
    private JsonNode parseJsonResult(String answer) throws Exception {
        String text = answer == null ? "" : answer.trim();
        // 移除 R1 等模型的思维链标签
        text = text.replaceAll("(?i)<think>[\\s\\S]*?</think>", "").trim();
        
        // 提取 Markdown 代码块内部的 JSON 字符串
        Matcher fencedMatcher = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE).matcher(text);
        if (fencedMatcher.find()) text = fencedMatcher.group(1).trim();
        
        // 如果清洗后仍不以左花括号开头，尝试正则提取第一个完整的花括号对象
        if (!text.startsWith("{")) {
            Matcher objectMatcher = Pattern.compile("\\{[\\s\\S]*}").matcher(text);
            if (objectMatcher.find()) text = objectMatcher.group();
        }
        
        JsonNode raw;
        try {
            raw = mapper.readTree(text);
        } catch (Exception e) {
            // 对末尾可能多出的半角逗号进行兜底清除
            raw = mapper.readTree(text.replaceAll(",\\s*([}\\]])", "$1"));
        }
        
        if (raw == null || raw.isMissingNode() || !raw.isObject()) throw new IllegalArgumentException("API returned a non-object JSON result: " + truncate(text, 300));
        ObjectNode normalized = ((ObjectNode) raw).deepCopy();
        JsonNode d = raw.path("dimension_scores");
        
        // 归一化并提取所有支持的评分维度字段，提供同义词映射以适应多模型输出偏差
        ObjectNode dims = mapper.createObjectNode();
        dims.put("indoor_layout", number(d, "indoor_layout", "室内布局", "空间布局", "architecture", "建筑风格"));
        dims.put("wall_floor_material", number(d, "wall_floor_material", "墙地材质", "road_surface", "地面材质"));
        dims.put("furniture_fixtures", number(d, "furniture_fixtures", "家具设施", "facilities", "固定设施"));
        dims.put("window_door_style", number(d, "window_door_style", "门窗样式", "vegetation", "植被绿化"));
        dims.put("lighting_environment", number(d, "lighting_environment", "光照环境", "lighting_weather", "光照天气", "天气光线"));
        normalized.set("dimension_scores", dims);
        
        // 确保基本比对结构字段缺省存在
        if (!normalized.has("similar_points")) normalized.set("similar_points", mapper.createArrayNode());
        if (!normalized.has("difference_points")) normalized.set("difference_points", mapper.createArrayNode());
        if (!normalized.has("summary")) normalized.put("summary", "");
        if (!normalized.has("similarity_score")) normalized.put("similarity_score", 0);
        return normalized;
    }

    /**
     * 辅助数值提取器：查找多个可能的字段名，安全解析为 0 - 100 范围的整数。
     */
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

    /**
     * 提取或估算本次大模型推理所耗用的 Token 额度。
     * 若响应体中含有官方统计节点则直接取用；若没有，根据输入提示词长度、抽帧张数及返回文本长度进行保底公式估算。
     */
    private JsonNode extractUsage(JsonNode body, String prompt, String answer, int imageCount, String modelId,
                                  String recognitionMode, String videoAPath, String videoBPath) {
        JsonNode usage = body.path("usage");
        ObjectNode out = mapper.createObjectNode();
        int promptTokens = usage.path("prompt_tokens").asInt(usage.path("input_tokens").asInt(0));
        int completionTokens = usage.path("completion_tokens").asInt(usage.path("output_tokens").asInt(0));
        
        // 提取 Gemini 的 usage 节点
        if (promptTokens <= 0) promptTokens = body.path("usageMetadata").path("promptTokenCount").asInt(0);
        if (completionTokens <= 0) completionTokens = body.path("usageMetadata").path("candidatesTokenCount").asInt(0);
        
        // 保底手动估算 Token
        if (promptTokens <= 0) {
            boolean qwenVideo = "video".equalsIgnoreCase(recognitionMode) && modelId != null && modelId.toLowerCase(Locale.ROOT).contains("qwen");
            if (qwenVideo) {
                // 如果是通义千问视频识别模式，执行官方视频多模态 Token 计算公式
                promptTokens = qwenVideoTokens(videoAPath) + qwenVideoTokens(videoBPath) + prompt.length() / 2 + 150;
            } else {
                // 图片模式估算：字符数/2 + 抽帧数 * 300 Token (单张多模态图的标准底耗)
                promptTokens = Math.max(1, prompt.length() / 2 + imageCount * 300);
            }
        }
        if (completionTokens <= 0) {
            completionTokens = Math.max(1, (answer == null ? 0 : answer.length()) / 2);
        }
        out.put("prompt_tokens", promptTokens);
        out.put("completion_tokens", completionTokens);
        out.put("total_tokens", promptTokens + completionTokens);
        if (usage.isMissingNode() || usage.isNull() || usage.isEmpty()) out.put("estimated", true);
        return out;
    }

    /**
     * 估算 Token 备用工具方法。
     */
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

    /**
     * 判断是否是 Google Gemini 系列模型。
     */
    private boolean isGemini(String provider, String modelId) {
        String providerValue = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        String modelValue = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
        return providerValue.contains("google") || modelValue.contains("gemini");
    }

    /**
     * 补全拼接 Gemini GenerateContent 接口所需要的带 key 查询参数的 URL 地址。
     */
    private String geminiGenerateContentUrl(String baseUrl, String modelId, String apiKey) {
        String base = (baseUrl == null || baseUrl.isBlank()) ? "https://generativelanguage.googleapis.com/v1beta" : baseUrl.replaceAll("/+$", "");
        if (base.contains(":generateContent")) return base.contains("?") ? base : base + "?key=" + apiKey;
        String modelPath = modelId != null && modelId.startsWith("models/") ? modelId : "models/" + modelId;
        return base + "/" + modelPath + ":generateContent?key=" + apiKey;
    }

    /**
     * 阿里云通义千问 Qwen2-VL 视频内联 Token 核心计算公式逻辑。
     * 底层调用 ffprobe 分析视频帧数，按高度和宽度向下取整对齐 QWEN_IMAGE_FACTOR 因子，
     * 依据其算力白皮书中的数学模型计算总 Token 消耗量。
     */
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

    /**
     * 计算 Qwen2-VL 的有效提取视频帧数。
     */
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

    /**
     * 专门用于 Qwen Token 计算的 FFprobe 视频轨快速探测工具。
     */
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

    /**
     * 解析分数类型帧率。
     */
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

    /**
     * 生成模型调用异常时的默认兜底错误响应 JSON 结构。
     */
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

    /**
     * 新建模型请求调用审计记录并持久化（对发送 Payload 进行深拷贝脱敏处理）。
     */
    private ModelCallLog startLog(String taskId, String taskName, String modelId, String modelUrl,
                                  JsonNode requestPayload, LocalDateTime startedAt) {
        try {
            ModelCallLog log = new ModelCallLog();
            log.setTaskId(taskId);
            log.setTaskName(taskName);
            log.setModelId(modelId);
            // 保留审计所需的元数据，但禁止将访问凭证和二进制载荷持久化到数据库。
            log.setModelUrl(redactUrl(modelUrl));
            log.setRequestPayload(sanitizeForLog(requestPayload));
            log.setStartedAt(startedAt);
            log.setStatusCode("PROCESSING");
            logMapper.insert(log);
            return log;
        } catch (Exception e) {
            LOGGER.warn("Failed to start model call log for task {}", taskId, e);
            return null;
        }
    }

    /**
     * 更新模型接口最终响应审计记录。
     */
    private void finishLog(ModelCallLog log, JsonNode responseBody, LocalDateTime endedAt, int statusCode,
                            double inputTokens, double outputTokens) {
        if (log == null) return;
        try {
            log.setResponseBody(sanitizeForLog(responseBody));
            log.setEndedAt(endedAt);
            log.setStatusCode(String.valueOf(statusCode));
            log.setInputTokens(inputTokens);
            log.setOutputTokens(outputTokens);
            logMapper.updateById(log);
        } catch (Exception e) {
            LOGGER.warn("Failed to finish model call log for task {}", log.getTaskId(), e);
        }
    }

    /**
     * 解析本地媒体文件的 Path 路径。
     */
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

    /**
     * 截取字符串的最大长度，用于日志截断。
     */
    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 基于深拷贝清理审计数据，避免脱敏过程修改实际发送给模型的请求载荷。
     */
    JsonNode sanitizeForLog(JsonNode source) {
        return sanitizeForLog(source, null);
    }

    /**
     * 内部深拷贝递归清理 JSON 属性，如果检测到值是 Base64 媒体数据流，替换为 redacted 占位符。
     */
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

    /**
     * 判断字段属性是否需要脱敏。
     */
    private JsonNode sanitizeField(String key, JsonNode value, String mimeType) {
        if (value.isTextual() && "data".equals(key) && mimeType != null && mimeType.startsWith("video/")) {
            return mapper.getNodeFactory().textNode(redactedMarker("video", value.asText()));
        }
        return sanitizeForLog(value, mimeType);
    }

    /**
     * 依据字符串特征检测并对超长 Base64 或者是 video base64 媒体段进行占位脱敏。
     */
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

    /**
     * 正则粗筛判断一个字符串是否大概率是 Base64 编码。
     */
    private boolean looksLikeBase64(String value) {
        String candidate = value;
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma >= 0) candidate = value.substring(comma + 1);
        if (candidate.length() < 256) return false;
        int sampleLength = Math.min(candidate.length(), 512);
        return candidate.substring(0, sampleLength).matches("[A-Za-z0-9+/=\\r\\n]+");
    }

    /**
     * 脱敏记录的替换内容说明标记。
     */
    private String redactedMarker(String type, String value) {
        return "[redacted " + type + " payload, encoded_chars=" + value.length() + "]";
    }

    /**
     * 对请求 URL 中的 key= 敏感参数进行隐藏。
     */
    private String redactUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("([?&]key=)[^&]+", "$1[redacted]");
    }

    /** 视频物理属性探测结果 record */
    private record VideoProbe(int width, int height, int totalFrames, double fps) {
    }

    /** 模型 Payload 和内联标识 record */
    private record PayloadEnvelope(ObjectNode payload, boolean inlineVideo) {
    }
}
