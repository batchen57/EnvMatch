package com.envmatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Point;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_core.TermCriteria;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.ByteIndexer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacpp.avcodec;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import static org.bytedeco.javacpp.opencv_video.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 视频与图像媒体素材处理核心服务类。
 * 
 * <p>该服务底层依赖于系统的 FFmpeg/FFprobe 以及 OpenCV 本地库，负责：
 * <ul>
 *   <li>使用 FFprobe 探测和提取视频时长、分辨率、帧率及文件大小等元数据。</li>
 *   <li>使用 FFmpeg 剪辑和转码视频素材（针对原生视频对比模式）。</li>
 *   <li>提供两种抽帧采样算法：
 *     <ol>
 *       <li><b>固定抽帧（fixed）</b>：以指定帧率均匀抽取视频帧，调用 FFmpeg 命令行完成。</li>
 *       <li><b>感知采样（perceptual）</b>：基于 OpenCV 图像分析的智能抽帧算法。结合场景硬剪切检测（基于 HSV 色彩直方图变化率）和帧间运动幅度分析（基于 Shi-Tomasi 角点检测与 Lucas-Kanade 稀疏光流跟踪），过滤相似冗余帧并始终保留首尾帧，实现最大相似特征保留的“关键帧精简抽取”。</li>
 *     </ol>
 *   </li>
 * </ul>
 * </p>
 */
@Service
public class VideoService {
    
    // 感知采样相关算法阈值（须与遗留 Python 版本的 SceneDetect + 稀疏光流采样器保持严格对齐）
    
    /** 场景剪切/内容突变阈值：两帧 HSV 色彩均值差异大于此值时视为场景切换 */
    private static final double SCENE_THRESHOLD = 15.0;
    
    /** 最小场景长度限制：两个场景切换关键帧之间至少相隔 15 帧 */
    private static final int SCENE_MIN_LENGTH_FRAMES = 15;
    
    /** 用于计算光流运动的采样率：每秒处理 4 帧 */
    private static final double OPTICAL_FLOW_SAMPLE_FPS = 4.0;
    
    /** Shi-Tomasi 算法最大角点跟踪数量 */
    private static final int OPTICAL_FLOW_MAX_CORNERS = 100;
    
    /**  Shi-Tomasi 角点检测质量等级（得分阈值因子） */
    private static final double OPTICAL_FLOW_QUALITY_LEVEL = 0.3;
    
    /** 两个被跟踪角点之间的最小像素距离 */
    private static final double OPTICAL_FLOW_MIN_DISTANCE = 7.0;
    
    /** 角点检测的邻域窗口大小 */
    private static final int OPTICAL_FLOW_BLOCK_SIZE = 7;
    
    /** 触发抽取新帧的运动像素阈值比例：累积运动像素偏移量超过视频宽度的 5% 时触发关键帧提取 */
    private static final double OPTICAL_FLOW_MOTION_RATIO = 0.05;
    
    /** 光流角点强制重刷新周期（秒）：每 2.0 秒重新检测一次画面特征角点，防止长时间跟踪漂移 */
    private static final double OPTICAL_FLOW_REFRESH_SECONDS = 2.0;
    
    /** 单个视频感知采样的最大返回帧数限制，超过此数将通过等距降采样裁剪 */
    private static final int PERCEPTUAL_MAX_FRAMES = 20;
    
    /** OpenCV 动态链接库本地加载状态标识 */
    private static volatile boolean openCvLoaded;

    /** 系统生成的素材存储目录路径 */
    private final Path storageDir;
    
    /** JSON 对象解析与映射器 */
    private final ObjectMapper mapper;

    /**
     * 构造函数，初始化存储根目录。
     */
    public VideoService(@Value("${envmatch.storage-dir:storage}") String storageDir, ObjectMapper mapper) {
        this.storageDir = Path.of(storageDir);
        this.mapper = mapper;
    }

    /**
     * 探测指定媒体素材路径的元数据（时长、分辨率、文件大小、FPS）。
     *
     * @param videoPath 素材物理路径
     * @return 封装好的 {@link VideoMetadata} 对象
     */
    public VideoMetadata metadata(String videoPath) {
        Path path = Path.of(videoPath);
        double sizeMb = fileSizeMb(path);
        
        // 1. 如果是静态图片素材，直接通过 Java ImageIO 读取分辨率
        if (isImage(path)) {
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image != null) return new VideoMetadata(0.0, image.getWidth() + "x" + image.getHeight(), sizeMb, 0.0);
            } catch (IOException ignored) {
            }
            return new VideoMetadata(0.0, "unknown", sizeMb, 0.0);
        }

        // 2. 如果是视频素材，使用 FFmpegFrameGrabber 获取元数据
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(path.toFile())) {
            grabber.start();
            double duration = grabber.getLengthInTime() / 1000000.0;
            String resolution = grabber.getImageWidth() + "x" + grabber.getImageHeight();
            double fps = grabber.getFrameRate();
            grabber.stop();
            return new VideoMetadata(duration, resolution, sizeMb, fps);
        } catch (Exception e) {
            return new VideoMetadata(0.0, "unknown", sizeMb, 0.0);
        }
    }

    /**
     * 兼容性重载方法：从视频中提取关键帧（使用默认 0 - 15 秒区间配置）。
     */
    public List<String> extractFrames(String videoPath, String taskId, String suffix, int fps, int resolution, String samplingType) {
        // Backward-compatible entry point. New tasks use the configurable interval overload below.
        return extractFrames(videoPath, taskId, suffix, fps, resolution, samplingType, 0.0, 15.0);
    }

    /**
     * 核心抽帧方法：从指定视频中抽取关键帧列表。
     *
     * @param videoPath    原视频路径
     * @param taskId       任务 ID
     * @param suffix       区分素材 A/B 的后缀标识
     * @param fps          目标抽帧频率（仅适用于 fixed 模式）
     * @param resolution   目标抽帧分辨率高宽（如 720 代表高度缩放至 720 像素，宽度等比例缩放）
     * @param samplingType 采样类型：fixed（均匀固定抽帧） 或 perceptual（感知采样）
     * @param startSeconds 裁剪的起始时间（秒）
     * @param endSeconds   裁剪的结束时间（秒）
     * @return 抽取的图片存储路径列表（按时序升序排列）
     */
    public List<String> extractFrames(String videoPath, String taskId, String suffix, int fps, int resolution,
                                      String samplingType, double startSeconds, double endSeconds) {
        Path source = Path.of(videoPath);
        Path outDir = storageDir.resolve(taskId + "_" + suffix + "_frames");
        try {
            Files.createDirectories(outDir);
        } catch (IOException ignored) {
        }

        // 1. 如果源文件是单张图片，直接等比例缩放并复制，返回其 JPEG 格式路径
        if (isImage(source)) {
            Path out = outDir.resolve("frame_001.jpg");
            copyImageAsJpeg(source, out, resolution);
            return listJpegs(outDir);
        }

        List<String> frames = List.of();
        // 2. 感知采样模式：执行 OpenCV 关键帧分析算法
        if ("perceptual".equalsIgnoreCase(samplingType)) {
            frames = extractPerceptualFrames(source, outDir, resolution, startSeconds, endSeconds);
        }

        // 3. 固定抽帧模式（或感知抽帧失败/回落）：使用 FFmpegFrameGrabber 对时段裁剪区进行均匀间隔抽帧
        if (frames.isEmpty()) {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(source.toFile())) {
                grabber.start();
                double startMicros = startSeconds * 1000000.0;
                double endMicros = endSeconds * 1000000.0;
                if (startMicros > 0) {
                    grabber.setTimestamp((long) startMicros);
                }
                
                double frameIntervalMicros = 1000000.0 / Math.max(1, fps);
                double nextFrameMicros = startMicros;
                
                OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
                int count = 1;
                Frame frame;
                
                while ((frame = grabber.grabImage()) != null) {
                    long currentMicros = grabber.getTimestamp();
                    if (endSeconds > 0 && currentMicros > endMicros) {
                        break;
                    }
                    if (currentMicros >= nextFrameMicros) {
                        Mat mat = converter.convert(frame);
                        if (mat != null && !mat.empty()) {
                            int width = Math.max(1, (int) Math.round(mat.cols() * (resolution / (double) mat.rows())));
                            Mat resized = new Mat();
                            Size targetSize = new Size(width, resolution);
                            resize(mat, resized, targetSize);
                            
                            Path out = outDir.resolve(String.format(Locale.ROOT, "frame_%03d.jpg", count++));
                            imwrite(out.toString(), resized);
                            resized.release();
                            targetSize.close();
                            
                            nextFrameMicros += frameIntervalMicros;
                        }
                    }
                }
                grabber.stop();
            } catch (Exception ignored) {
            }
            frames = listJpegs(outDir);
        }

        // 4. 安全保底：如果抽取的帧数过少（少于 5 帧，通常出现在特短视频中），自动注入 5 张时序均匀覆盖的帧
        if (frames.size() < 5) {
            fallbackFrames(source, outDir, resolution, startSeconds, endSeconds);
            frames = listJpegs(outDir);
        }
        
        // 去重并按文件路径升序返回
        return frames.stream().distinct().sorted().toList();
    }

    /**
     * 兼容性重载方法：裁剪和转码视频（使用默认 0 - 15 秒区间配置）。
     */
    public String preprocessVideo(String videoPath, String taskId, String suffix, int targetRes) {
        return preprocessVideo(videoPath, taskId, suffix, targetRes, true, 0.0, 15.0);
    }

    /**
     * 针对视频对比模式，进行视频时段剪切与等比例缩放转码。
     *
     * @param videoPath    原始视频路径
     * @param taskId       任务 ID
     * @param suffix       后缀标识
     * @param targetRes    目标高度分辨率
     * @param resize       是否进行分辨率等比例缩减缩放
     * @param startSeconds 起始裁剪时间（秒）
     * @param endSeconds   结束裁剪时间（秒）
     * @return 处理转码后的视频在存储目录下的相对路径
     */
    public String preprocessVideo(String videoPath, String taskId, String suffix, int targetRes,
                                  boolean resize, double startSeconds, double endSeconds) {
        Path out = storageDir.resolve(taskId + "_" + suffix + "_processed.mp4");
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            grabber.start();
            double startMicros = startSeconds * 1000000.0;
            double endMicros = endSeconds * 1000000.0;
            if (startMicros > 0) {
                grabber.setTimestamp((long) startMicros);
            }
            
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            
            if (resize) {
                width = Math.max(2, (int) Math.round(width * (targetRes / (double) height)));
                if (width % 2 != 0) width++; // H.264 要求宽高为偶数
                height = targetRes;
                if (height % 2 != 0) height++;
            }
            
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(out.toFile(), width, height, grabber.getAudioChannels())) {
                recorder.setFormat("mp4");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                recorder.setVideoOption("crf", "28");
                recorder.setVideoOption("preset", "faster");
                if (grabber.getAudioChannels() > 0) {
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                }
                recorder.setFrameRate(grabber.getFrameRate());
                recorder.setSampleRate(grabber.getSampleRate());
                recorder.start();
                
                Frame frame;
                while ((frame = grabber.grab()) != null) {
                    if (endSeconds > 0 && grabber.getTimestamp() > endMicros) {
                        break;
                    }
                    recorder.record(frame);
                }
                recorder.stop();
            }
            grabber.stop();
            if (Files.exists(out)) return toStoragePath(out);
            throw new IllegalStateException("Video clipping failed to create file.");
        } catch (Exception e) {
            throw new IllegalStateException("Video clipping failed for input " + videoPath, e);
        }
    }

    /**
     * 保底抽帧方法。当分析出错或视频极短导致采集帧数小于 5 帧时，
     * 在裁剪区间内等距取 5 个时间戳点，通过 FFmpegFrameGrabber 提取 5 张帧。
     */
    private void fallbackFrames(Path source, Path outDir, int resolution, double startSeconds, double endSeconds) {
        VideoMetadata metadata = metadata(source.toString());
        double effectiveEnd = metadata.duration() > 0 ? Math.min(endSeconds, metadata.duration()) : endSeconds;
        double duration = Math.max(effectiveEnd - startSeconds, 0.001);
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(source.toFile())) {
            grabber.start();
            OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
            
            for (int i = 0; i < 5; i++) {
                double timestamp = startSeconds + duration * i / 4.0;
                timestamp = Math.max(startSeconds, Math.min(effectiveEnd - 0.001, timestamp));
                grabber.setTimestamp((long) (timestamp * 1000000.0));
                
                Frame frame = grabber.grabImage();
                if (frame != null) {
                    Mat mat = converter.convert(frame);
                    if (mat != null && !mat.empty()) {
                        int width = Math.max(1, (int) Math.round(mat.cols() * (resolution / (double) mat.rows())));
                        Mat resized = new Mat();
                        Size targetSize = new Size(width, resolution);
                        resize(mat, resized, targetSize);
                        
                        Path out = outDir.resolve(String.format(Locale.ROOT, "fallback_%03d.jpg", i + 1));
                        imwrite(out.toString(), resized);
                        resized.release();
                        targetSize.close();
                    }
                }
            }
            grabber.stop();
        } catch (Exception ignored) {
        }
    }

    /**
     * 核心 OpenCV 感知抽帧逻辑执行器。
     * 自动加载 OpenCV DLL 依赖，调用底层计算出最优的关键帧索引，然后通过 FFmpeg 的 raw 管道逐帧拉取像素并保存为缩放后的 JPEG 图像。
     */
    private List<String> extractPerceptualFrames(Path source, Path outDir, int resolution,
                                                 double startSeconds, double endSeconds) {
        try {
            loadOpenCv();
            VideoMetadata metadata = metadata(source.toString());
            FrameSize frameSize = parseFrameSize(metadata.resolution());
            if (frameSize == null) return List.of();
            double fps = metadata.fps() > 0 ? metadata.fps() : 25.0;
            
            // 1. 使用 OpenCV 视频特征提取分析算法计算出应当提取的帧的时序索引列表 (基于首尾保留、场景切变与运动光流)
            List<Integer> selected = detectPerceptualFrames(source, frameSize, fps, startSeconds, endSeconds);
            if (selected.isEmpty()) return List.of();

            List<String> frames = new ArrayList<>();
            int sourceFrameOffset = Math.max(0, (int) Math.round(startSeconds * fps));
            
            // 2. 根据时序索引进行二次遍历，并通过 FFmpeg rawvideo 管道解码相应帧以极速保存
            decodeFrames(source, frameSize, startSeconds, endSeconds, (frameIndex, frame) -> {
                if (!selected.contains(frameIndex)) return frameIndex <= selected.get(selected.size() - 1);
                int height = Math.max(1, resolution);
                int width = Math.max(1, (int) Math.round(frame.cols() * (height / (double) frame.rows())));
                Mat resized = new Mat();
                Size targetSize = new Size(width, height);
                try {
                    resize(frame, resized, targetSize);
                    Path out = outDir.resolve(String.format(Locale.ROOT, "p_%06d.jpg", sourceFrameOffset + frameIndex));
                    imwrite(out.toString(), resized);
                    if (Files.exists(out)) frames.add(toStoragePath(out));
                } finally {
                    resized.release();
                    targetSize.close();
                }
                return frameIndex < selected.get(selected.size() - 1);
            });
            return frames.stream().distinct().sorted().toList();
        } catch (Exception | LinkageError e) {
            // OpenCV 本地库加载失败或分析内部出错时，打印日志并回落到固定/保底采样抽帧
            return List.of();
        }
    }

    /**
     * 感知采样帧检测核心算法。
     * 通过双指标联合驱动画面判断：
     * 1. 场景大变动（Scene Cuts）：计算前后两帧直方图在 HSV 空间的差值（基于均值绝对差 contentScore 算法），若超出阈值且满足最小长度限制，则视为硬剪辑或大视角变换。
     * 2. 帧间运动幅度（Optical Flow）：使用 Shi-Tomasi 算法检测当前帧角点，使用 Lucas-Kanade 稀疏光流法跟踪其位移。对有效点计算偏移距离中位数并进行时序累加，当画面累积运动偏移量超出视频宽度的一定比例时提取一帧。
     */
    private List<Integer> detectPerceptualFrames(Path source, FrameSize frameSize, double fps,
                                                 double startSeconds, double endSeconds) throws Exception {
        Set<Integer> selected = new LinkedHashSet<>();
        // 规则一：始终保留区间的第一帧
        selected.add(0);

        Mat gray = new Mat();
        Mat hsv = new Mat();
        Mat[] previousHsv = {null};
        Mat[] previousFlowGray = {null};
        Mat[] previousPoints = {null};
        double[] motionAccum = {0.0};
        int[] lastFrameIndex = {-1};
        int[] lastSceneCut = {0};
        
        // 降频光流采样步长，避免对每一帧都运行光流追踪以节省 CPU
        int flowStep = Math.max(1, (int) (fps / OPTICAL_FLOW_SAMPLE_FPS));
        // 特征点刷新周期（帧数）
        int refreshFrames = Math.max(1, (int) fps * (int) OPTICAL_FLOW_REFRESH_SECONDS);

        // 流式解码读取视频像素流
        try {
            decodeFrames(source, frameSize, startSeconds, endSeconds, (frameIndex, frame) -> {
                lastFrameIndex[0] = frameIndex;
                
                // 转化为 HSV 色彩空间进行场景突变对比
                cvtColor(frame, hsv, COLOR_BGR2HSV);
                if (previousHsv[0] != null
                        && frameIndex - lastSceneCut[0] >= SCENE_MIN_LENGTH_FRAMES
                        && contentScore(previousHsv[0], hsv) > SCENE_THRESHOLD) {
                    // 内容变化幅度超出 SCENE_THRESHOLD，提取当前帧并更新场景起点
                    selected.add(frameIndex);
                    lastSceneCut[0] = frameIndex;
                }
                if (previousHsv[0] != null) previousHsv[0].release();
                previousHsv[0] = hsv.clone();

                // 进行基于 Shi-Tomasi & Lucas-Kanade 光流的运动量化分析
                if (frameIndex == 0 || frameIndex % flowStep == 0) {
                    cvtColor(frame, gray, COLOR_BGR2GRAY);
                    
                    if (previousFlowGray[0] == null) {
                        previousFlowGray[0] = gray.clone();
                        previousPoints[0] = detectCorners(gray);
                    } else if (frameIndex % refreshFrames == 0) {
                        // 强制刷新角点，防止物体移出视线或遮挡导致丢失跟踪点
                        previousFlowGray[0].release();
                        previousFlowGray[0] = gray.clone();
                        if (previousPoints[0] != null) previousPoints[0].release();
                        previousPoints[0] = detectCorners(gray);
                    } else if (previousPoints[0] != null && !previousPoints[0].empty()) {
                        Mat currentPoints = new Mat();
                        Mat status = new Mat();
                        Mat errors = new Mat();
                        Size winSize = new Size(21, 21);
                        TermCriteria criteria = new TermCriteria(TermCriteria.MAX_ITER | TermCriteria.EPS, 30, 0.01);
                        
                        try {
                            // 计算稀疏光流，查找当前帧特征点的新像素位置
                            calcOpticalFlowPyrLK(previousFlowGray[0], gray, previousPoints[0], currentPoints, status, errors,
                                    winSize, 3, criteria, 0, 1e-4);

                            TrackedPoints tracked = trackedPoints(previousPoints[0], currentPoints, status);
                            if (!tracked.distances().isEmpty()) {
                                // 累加前后两帧特征点像素位移的中位数（排除突跳异常噪点影响）
                                motionAccum[0] += median(tracked.distances());
                                
                                // 当累积运动距离超过视频宽度的 OPTICAL_FLOW_MOTION_RATIO 时触发抽帧
                                if (motionAccum[0] > frame.cols() * OPTICAL_FLOW_MOTION_RATIO) {
                                    selected.add(frameIndex);
                                    motionAccum[0] = 0.0;
                                    // 抽帧后立即重刷特征角点，跟踪全新的场景运动轨迹
                                    previousPoints[0].release();
                                    previousPoints[0] = detectCorners(gray);
                                    tracked.current().release();
                                } else {
                                    // 未达到提取阈值，保存当前保留点以便下一帧继续追踪
                                    previousPoints[0].release();
                                    previousPoints[0] = tracked.current();
                                }
                            } else {
                                tracked.current().release();
                            }
                        } finally {
                            currentPoints.release();
                            status.release();
                            errors.release();
                            winSize.close();
                            criteria.close();
                        }
                        previousFlowGray[0].release();
                        previousFlowGray[0] = gray.clone();
                    } else {
                        previousFlowGray[0].release();
                        previousFlowGray[0] = gray.clone();
                    }
                }
                return true;
            });
        } finally {
            // 释放 OpenCV Mat 资源，防内存泄露
            if (previousHsv[0] != null) previousHsv[0].release();
            if (previousFlowGray[0] != null) previousFlowGray[0].release();
            if (previousPoints[0] != null) previousPoints[0].release();
            gray.release();
            hsv.release();
        }

        // 规则二：始终保留视频最后一帧
        if (lastFrameIndex[0] >= 0) selected.add(lastFrameIndex[0]);
        
        // 3. 对抽取的关键帧索引根据预设最大值进行等距排序缩减（避免生成的合图大小超出大模型 Token 限制）
        return reduceCandidateIndices(selected.stream().sorted().toList(), PERCEPTUAL_MAX_FRAMES);
    }

    /**
     * 通过 JavaCV FFmpegFrameGrabber 直接解码视频流，并提取每一帧的 Mat 像素对象。
     * 这绕过了外部进程管道调用，并且自带所有 FFmpeg 解码器的容器兼容性支持。
     */
    private void decodeFrames(Path source, FrameSize frameSize, double startSeconds, double endSeconds,
                              FrameConsumer consumer) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(source.toFile())) {
            grabber.start();
            double startMicros = startSeconds * 1000000.0;
            double endMicros = endSeconds * 1000000.0;
            
            if (startMicros > 0) {
                grabber.setTimestamp((long) startMicros);
            }
            
            OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
            int frameIndex = 0;
            Frame frame;
            
            while ((frame = grabber.grabImage()) != null) {
                if (endSeconds > 0 && grabber.getTimestamp() > endMicros) {
                    break;
                }
                Mat mat = converter.convert(frame);
                if (mat != null && !mat.empty()) {
                    if (!consumer.accept(frameIndex, mat)) {
                        break;
                    }
                    frameIndex++;
                }
            }
            grabber.stop();
        }
    }

    /**
     * 解析分辨率高宽值（如 "1280x720" => FrameSize(1280, 720)）
     */
    private FrameSize parseFrameSize(String resolution) {
        try {
            String[] parts = resolution.toLowerCase(Locale.ROOT).split("x");
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            return width > 0 && height > 0 ? new FrameSize(width, height) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 格式化秒级时间戳为 FFmpeg 所支持的 3 位小数的字符串格式
     */
    private String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0.0, seconds));
    }

    /**
     * 当抽取的关键帧索引集超出限制值时，对其进行等距降采样缩减。
     *
     * @param candidates 候选帧索引列表
     * @param limit      保留数量限制
     * @return 缩减后的索引列表
     */
    static List<Integer> reduceCandidateIndices(List<Integer> candidates, int limit) {
        List<Integer> selected = candidates.stream().distinct().sorted().toList();
        if (selected.size() <= limit) return selected;
        if (limit <= 1) return List.of(selected.get(0));

        List<Integer> reduced = new ArrayList<>(limit);
        double step = (selected.size() - 1) / (double) (limit - 1);
        for (int i = 0; i < limit; i++) {
            reduced.add(selected.get((int) Math.round(i * step)));
        }
        return reduced.stream().distinct().sorted().toList();
    }

    /**
     * 计算前后两帧直方图在 HSV 空间的差值内容得分。
     *
     * @param previousHsv 上一帧的 HSV 图像
     * @param currentHsv  当前帧的 HSV 图像
     * @return 内容色差得分（值越大代表两个画面差异越明显）
     */
    static double contentScore(Mat previousHsv, Mat currentHsv) {
        MatVector previousChannels = new MatVector();
        MatVector currentChannels = new MatVector();
        try {
            split(previousHsv, previousChannels);
            split(currentHsv, currentChannels);
            double score = 0.0;
            // 分别计算 H、S、V 三个色彩分量的平均绝对差
            for (int i = 0; i < 3; i++) {
                Mat difference = new Mat();
                try {
                    absdiff(previousChannels.get(i), currentChannels.get(i), difference);
                    org.bytedeco.javacpp.opencv_core.Scalar m = mean(difference);
                    try {
                        score += m.get(0);
                    } finally {
                        m.close();
                    }
                } finally {
                    difference.release();
                }
            }
            return score / 3.0;
        } finally {
            for (long i = 0; i < previousChannels.size(); i++) {
                if (previousChannels.get(i) != null) previousChannels.get(i).release();
            }
            previousChannels.deallocate();
            for (long i = 0; i < currentChannels.size(); i++) {
                if (currentChannels.get(i) != null) currentChannels.get(i).release();
            }
            currentChannels.deallocate();
        }
    }

    /**
     * 在灰度帧上检测特征角点（基于 Shi-Tomasi goodFeaturesToTrack 算法）。
     */
    private Mat detectCorners(Mat gray) {
        Mat corners = new Mat();
        Mat mask = new Mat();
        try {
            goodFeaturesToTrack(gray, corners, OPTICAL_FLOW_MAX_CORNERS,
                    OPTICAL_FLOW_QUALITY_LEVEL, OPTICAL_FLOW_MIN_DISTANCE, mask,
                    OPTICAL_FLOW_BLOCK_SIZE, false, 0.04);
            return corners;
        } finally {
            mask.release();
        }
    }

    /**
     * 根据 Lucas-Kanade 稀疏光流计算得出的状态掩码，过滤出跟踪成功的有效点对，并返回其对应的帧间偏移欧氏距离集。
     */
    private TrackedPoints trackedPoints(Mat previous, Mat current, Mat status) {
        int total = (int) previous.total();
        int rows = previous.rows();
        int cols = previous.cols();
        
        FloatIndexer prevIdx = previous.createIndexer();
        FloatIndexer currIdx = current.createIndexer();
        org.bytedeco.javacpp.indexer.UByteIndexer statusIdx = status.createIndexer();
        
        try {
            int validCount = 0;
            for (int i = 0; i < total; i++) {
                int r = rows > 1 ? i : 0;
                int c = rows > 1 ? 0 : i;
                if (statusIdx.get(r, c) == 1) {
                    validCount++;
                }
            }
            
            Mat validCurrent = new Mat(validCount, 1, CV_32FC2);
            FloatIndexer validIdx = validCurrent.createIndexer();
            try {
                List<Double> distances = new ArrayList<>();
                
                int destIndex = 0;
                for (int i = 0; i < total; i++) {
                    int r = rows > 1 ? i : 0;
                    int c = rows > 1 ? 0 : i;
                    if (statusIdx.get(r, c) == 1) {
                        float px = prevIdx.get(r, c, 0);
                        float py = prevIdx.get(r, c, 1);
                        float cx = currIdx.get(r, c, 0);
                        float cy = currIdx.get(r, c, 1);
                        
                        validIdx.put(destIndex, 0, 0, cx);
                        validIdx.put(destIndex, 0, 1, cy);
                        destIndex++;
                        
                        double dx = cx - px;
                        double dy = cy - py;
                        distances.add(Math.hypot(dx, dy));
                    }
                }
                
                return new TrackedPoints(validCurrent, distances);
            } finally {
                validIdx.release();
            }
        } finally {
            prevIdx.release();
            currIdx.release();
            statusIdx.release();
        }
    }

    /**
     * 辅助工具：计算一列 Double 值的中位数。
     */
    private double median(List<Double> values) {
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }

    /**
     * 本地安全加载 OpenCV 链接库。
     */
    private static void loadOpenCv() {
        if (openCvLoaded) return;
        synchronized (VideoService.class) {
            if (!openCvLoaded) {
                Loader.load(org.bytedeco.javacpp.opencv_core.class);
                openCvLoaded = true;
            }
        }
    }

    /**
     * 遍历特定物理目录下所有提取出来的 jpeg 预览文件列表。
     */
    private List<String> listJpegs(Path dir) {
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jpg"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(this::toStoragePath)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * 检测一个文件路径的扩展名是否为静态图片类型。
     */
    private boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".bmp");
    }

    /**
     * 将其他格式的图片转码或复制为标准 JPEG 图像文件，并进行最大高度限幅缩放。
     */
    private void copyImageAsJpeg(Path input, Path output, int targetHeight) {
        try {
            BufferedImage image = ImageIO.read(input.toFile());
            if (image == null) return;
            int height = targetHeight > 0 ? targetHeight : image.getHeight();
            int width = Math.max(1, (int) Math.round(image.getWidth() * (height / (double) image.getHeight())));
            BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(image.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();
            ImageIO.write(resized, "jpg", output.toFile());
        } catch (IOException ignored) {
        }
    }

    /**
     * 将绝对文件物理路径标准化为供 HTTP 访问的相对存储资源 URI 路径（如 "storage/task1_A_frames/frame_001.jpg"）。
     */
    private String toStoragePath(Path path) {
        Path absoluteStorage = storageDir.toAbsolutePath().normalize();
        Path absolutePath = path.toAbsolutePath().normalize();
        try {
            return storageDir.getFileName() + "/" + absoluteStorage.relativize(absolutePath).toString().replace("\\", "/");
        } catch (Exception e) {
            return path.toString().replace("\\", "/");
        }
    }

    /**
     * 解析平均帧率字符串。如 "25/1" 代表 25 FPS，"24000/1001" 代表 23.976 FPS。
     */
    private double parseFps(String raw) {
        try {
            if (raw.contains("/")) {
                String[] parts = raw.split("/");
                double a = Double.parseDouble(parts[0]);
                double b = Double.parseDouble(parts[1]);
                return b == 0 ? 0.0 : a / b;
            }
            return Double.parseDouble(raw);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 获取物理文件的大小并转换为 MB（保留两位小数）。
     */
    private double fileSizeMb(Path path) {
        try {
            return Math.round(Files.size(path) / 1024.0 / 1024.0 * 100.0) / 100.0;
        } catch (IOException e) {
            return 0.0;
        }
    }

    /** 光流跟踪的有效点物理位置及位移集 */
    private record TrackedPoints(Mat current, List<Double> distances) {
    }

    /** 图像高宽规格 record */
    private record FrameSize(int width, int height) {
    }

    /** 帧像素流迭代消费接口 */
    @FunctionalInterface
    private interface FrameConsumer {
        boolean accept(int frameIndex, Mat frame) throws Exception;
    }
}
