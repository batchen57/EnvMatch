package com.envmatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.CvType;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
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

@Service
public class VideoService {
    // Keep these values aligned with the legacy Python SceneDetect + sparse optical-flow sampler.
    private static final double SCENE_THRESHOLD = 15.0;
    private static final int SCENE_MIN_LENGTH_FRAMES = 15;
    private static final double OPTICAL_FLOW_SAMPLE_FPS = 4.0;
    private static final int OPTICAL_FLOW_MAX_CORNERS = 100;
    private static final double OPTICAL_FLOW_QUALITY_LEVEL = 0.3;
    private static final double OPTICAL_FLOW_MIN_DISTANCE = 7.0;
    private static final int OPTICAL_FLOW_BLOCK_SIZE = 7;
    private static final double OPTICAL_FLOW_MOTION_RATIO = 0.05;
    private static final double OPTICAL_FLOW_REFRESH_SECONDS = 2.0;
    private static final int PERCEPTUAL_MAX_FRAMES = 20;
    private static volatile boolean openCvLoaded;

    private final Path storageDir;
    private final ObjectMapper mapper;

    public VideoService(@Value("${envmatch.storage-dir:storage}") String storageDir, ObjectMapper mapper) {
        this.storageDir = Path.of(storageDir);
        this.mapper = mapper;
    }

    public VideoMetadata metadata(String videoPath) {
        Path path = Path.of(videoPath);
        double sizeMb = fileSizeMb(path);
        if (isImage(path)) {
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image != null) return new VideoMetadata(0.0, image.getWidth() + "x" + image.getHeight(), sizeMb, 0.0);
            } catch (IOException ignored) {
            }
            return new VideoMetadata(0.0, "unknown", sizeMb, 0.0);
        }

        try {
            ProcessResult result = run(List.of("ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", path.toString()), 30);
            if (result.exitCode() != 0 || result.stdout().isBlank()) return new VideoMetadata(0.0, "unknown", sizeMb, 0.0);
            JsonNode root = mapper.readTree(result.stdout());
            JsonNode video = null;
            for (JsonNode stream : root.path("streams")) {
                if ("video".equals(stream.path("codec_type").asText())) {
                    video = stream;
                    break;
                }
            }
            if (video == null) return new VideoMetadata(0.0, "unknown", sizeMb, 0.0);
            double duration = root.path("format").path("duration").asDouble(0.0);
            String resolution = video.path("width").asInt() + "x" + video.path("height").asInt();
            double fps = parseFps(video.path("avg_frame_rate").asText("0"));
            return new VideoMetadata(duration, resolution, sizeMb, fps);
        } catch (Exception e) {
            return new VideoMetadata(0.0, "unknown", sizeMb, 0.0);
        }
    }

    public List<String> extractFrames(String videoPath, String taskId, String suffix, int fps, int resolution, String samplingType) {
        // Backward-compatible entry point. New tasks use the configurable interval overload below.
        return extractFrames(videoPath, taskId, suffix, fps, resolution, samplingType, 0.0, 15.0);
    }

    public List<String> extractFrames(String videoPath, String taskId, String suffix, int fps, int resolution,
                                      String samplingType, double startSeconds, double endSeconds) {
        Path source = Path.of(videoPath);
        Path outDir = storageDir.resolve(taskId + "_" + suffix + "_frames");
        try {
            Files.createDirectories(outDir);
        } catch (IOException ignored) {
        }

        if (isImage(source)) {
            Path out = outDir.resolve("frame_001.jpg");
            copyImageAsJpeg(source, out, resolution);
            return listJpegs(outDir);
        }

        List<String> frames = List.of();
        if ("perceptual".equalsIgnoreCase(samplingType)) {
            // Perceptual and fixed sampling must observe the same time interval so AI inputs stay comparable.
            frames = extractPerceptualFrames(source, outDir, resolution, startSeconds, endSeconds);
        }

        if (frames.isEmpty()) {
            // Also serves as a deterministic fallback when OpenCV cannot initialize or analyze the source.
            String vf = "fps=" + Math.max(1, fps) + ",scale=-1:" + Math.max(1, resolution);
            Path pattern = outDir.resolve("frame_%03d.jpg");
            try {
                run(List.of("ffmpeg", "-y", "-ss", formatSeconds(startSeconds), "-t",
                        formatSeconds(endSeconds - startSeconds), "-i", source.toString(),
                        "-vf", vf, "-qscale:v", "2", pattern.toString()), 180);
            } catch (Exception ignored) {
            }
            frames = listJpegs(outDir);
        }

        if (frames.size() < 5) {
            fallbackFrames(source, outDir, resolution, startSeconds, endSeconds);
            frames = listJpegs(outDir);
        }
        return frames.stream().distinct().sorted().toList();
    }

    public String preprocessVideo(String videoPath, String taskId, String suffix, int targetRes) {
        return preprocessVideo(videoPath, taskId, suffix, targetRes, true, 0.0, 15.0);
    }

    public String preprocessVideo(String videoPath, String taskId, String suffix, int targetRes,
                                  boolean resize, double startSeconds, double endSeconds) {
        Path out = storageDir.resolve(taskId + "_" + suffix + "_processed.mp4");
        try {
            // Native video-model requests receive the same bounded interval used to generate preview frames.
            List<String> command = new ArrayList<>(List.of(
                    "ffmpeg", "-y", "-ss", formatSeconds(startSeconds), "-t",
                    formatSeconds(endSeconds - startSeconds), "-i", videoPath
            ));
            if (resize) {
                command.addAll(List.of("-vf", "scale=-2:" + targetRes));
            }
            command.addAll(List.of("-vcodec", "libx264", "-crf", "28", "-preset", "faster",
                    "-acodec", "aac", out.toString()));
            ProcessResult result = run(command, 300);
            if (result.exitCode() == 0 && Files.exists(out)) return toStoragePath(out);
            throw new IllegalStateException("FFmpeg exited with code " + result.exitCode());
        } catch (Exception e) {
            // Never fall back to the full source video: doing so would silently violate the user's interval.
            throw new IllegalStateException("Video clipping failed for input " + videoPath, e);
        }
    }

    private void fallbackFrames(Path source, Path outDir, int resolution, double startSeconds, double endSeconds) {
        VideoMetadata metadata = metadata(source.toString());
        double effectiveEnd = metadata.duration() > 0 ? Math.min(endSeconds, metadata.duration()) : endSeconds;
        double duration = Math.max(effectiveEnd - startSeconds, 0.001);
        // Five deterministic, evenly spaced frames avoid the cross-run drift caused by random jitter.
        for (int i = 0; i < 5; i++) {
            double timestamp = startSeconds + duration * i / 4.0;
            timestamp = Math.max(startSeconds, Math.min(effectiveEnd - 0.001, timestamp));
            Path out = outDir.resolve(String.format(Locale.ROOT, "fallback_%03d.jpg", i + 1));
            try {
                run(List.of("ffmpeg", "-y", "-ss", formatSeconds(timestamp), "-i", source.toString(),
                        "-frames:v", "1", "-vf", "scale=-1:" + resolution, "-qscale:v", "2", out.toString()), 60);
            } catch (Exception ignored) {
            }
        }
    }

    private List<String> extractPerceptualFrames(Path source, Path outDir, int resolution,
                                                 double startSeconds, double endSeconds) {
        try {
            loadOpenCv();
            VideoMetadata metadata = metadata(source.toString());
            FrameSize frameSize = parseFrameSize(metadata.resolution());
            if (frameSize == null) return List.of();
            double fps = metadata.fps() > 0 ? metadata.fps() : 25.0;
            List<Integer> selected = detectPerceptualFrames(source, frameSize, fps, startSeconds, endSeconds);
            if (selected.isEmpty()) return List.of();

            List<String> frames = new ArrayList<>();
            // Detection indices are interval-relative; filenames retain approximate source frame numbers for auditability.
            int sourceFrameOffset = Math.max(0, (int) Math.round(startSeconds * fps));
            decodeFrames(source, frameSize, startSeconds, endSeconds, (frameIndex, frame) -> {
                if (!selected.contains(frameIndex)) return frameIndex <= selected.get(selected.size() - 1);
                int height = Math.max(1, resolution);
                int width = Math.max(1, (int) Math.round(frame.cols() * (height / (double) frame.rows())));
                Mat resized = new Mat();
                Imgproc.resize(frame, resized, new org.opencv.core.Size(width, height));
                Path out = outDir.resolve(String.format(Locale.ROOT, "p_%06d.jpg", sourceFrameOffset + frameIndex));
                Imgcodecs.imwrite(out.toString(), resized);
                resized.release();
                if (Files.exists(out)) frames.add(toStoragePath(out));
                return frameIndex < selected.get(selected.size() - 1);
            });
            return frames.stream().distinct().sorted().toList();
        } catch (Exception | LinkageError e) {
            return List.of();
        }
    }

    private List<Integer> detectPerceptualFrames(Path source, FrameSize frameSize, double fps,
                                                 double startSeconds, double endSeconds) throws Exception {
        Set<Integer> selected = new LinkedHashSet<>();
        selected.add(0);

        Mat gray = new Mat();
        Mat hsv = new Mat();
        Mat[] previousHsv = {null};
        Mat[] previousFlowGray = {null};
        MatOfPoint2f[] previousPoints = {null};
        double[] motionAccum = {0.0};
        int[] lastFrameIndex = {-1};
        int[] lastSceneCut = {0};
        int flowStep = Math.max(1, (int) (fps / OPTICAL_FLOW_SAMPLE_FPS));
        int refreshFrames = Math.max(1, (int) fps * (int) OPTICAL_FLOW_REFRESH_SECONDS);

        decodeFrames(source, frameSize, startSeconds, endSeconds, (frameIndex, frame) -> {
            lastFrameIndex[0] = frameIndex;
            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);
            if (previousHsv[0] != null
                    && frameIndex - lastSceneCut[0] >= SCENE_MIN_LENGTH_FRAMES
                    && contentScore(previousHsv[0], hsv) > SCENE_THRESHOLD) {
                // SceneDetect-style HSV content score captures hard cuts and large visual changes.
                selected.add(frameIndex);
                lastSceneCut[0] = frameIndex;
            }
            if (previousHsv[0] != null) previousHsv[0].release();
            previousHsv[0] = hsv.clone();

            if (frameIndex == 0 || frameIndex % flowStep == 0) {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
                if (previousFlowGray[0] == null) {
                    previousFlowGray[0] = gray.clone();
                    previousPoints[0] = detectCorners(gray);
                } else if (frameIndex % refreshFrames == 0) {
                    previousFlowGray[0].release();
                    previousFlowGray[0] = gray.clone();
                    if (previousPoints[0] != null) previousPoints[0].release();
                    previousPoints[0] = detectCorners(gray);
                } else if (previousPoints[0] != null && !previousPoints[0].empty()) {
                    MatOfPoint2f currentPoints = new MatOfPoint2f();
                    MatOfByte status = new MatOfByte();
                    MatOfFloat errors = new MatOfFloat();
                    Video.calcOpticalFlowPyrLK(previousFlowGray[0], gray, previousPoints[0], currentPoints, status, errors);

                    TrackedPoints tracked = trackedPoints(previousPoints[0], currentPoints, status);
                    if (!tracked.distances().isEmpty()) {
                        motionAccum[0] += median(tracked.distances());
                        if (motionAccum[0] > frame.cols() * OPTICAL_FLOW_MOTION_RATIO) {
                            // Accumulated median LK displacement captures camera movement within one scene.
                            selected.add(frameIndex);
                            motionAccum[0] = 0.0;
                            previousPoints[0].release();
                            previousPoints[0] = detectCorners(gray);
                        } else {
                            previousPoints[0].release();
                            previousPoints[0] = new MatOfPoint2f();
                            previousPoints[0].fromArray(tracked.current());
                        }
                    }
                    currentPoints.release();
                    status.release();
                    errors.release();
                    previousFlowGray[0].release();
                    previousFlowGray[0] = gray.clone();
                } else {
                    previousFlowGray[0].release();
                    previousFlowGray[0] = gray.clone();
                }
            }
            return true;
        });

        if (previousHsv[0] != null) previousHsv[0].release();
        if (previousFlowGray[0] != null) previousFlowGray[0].release();
        if (previousPoints[0] != null) previousPoints[0].release();
        gray.release();
        hsv.release();

        if (lastFrameIndex[0] >= 0) selected.add(lastFrameIndex[0]);
        return reduceCandidateIndices(selected.stream().sorted().toList(), PERCEPTUAL_MAX_FRAMES);
    }

    private void decodeFrames(Path source, FrameSize frameSize, double startSeconds, double endSeconds,
                              FrameConsumer consumer) throws Exception {
        // FFmpeg handles codec/container compatibility; OpenCV only receives raw BGR pixels for analysis.
        // This avoids VideoCapture failures seen with the packaged OpenCV codecs on Windows.
        Process process = new ProcessBuilder("ffmpeg", "-v", "error", "-noautorotate",
                "-ss", formatSeconds(startSeconds), "-t", formatSeconds(endSeconds - startSeconds),
                "-i", source.toString(),
                "-f", "rawvideo", "-pix_fmt", "bgr24", "pipe:1").start();
        CompletableFuture<String> errorOutput = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getErrorStream().readAllBytes());
            } catch (IOException e) {
                return "";
            }
        });

        int frameBytes = Math.multiplyExact(Math.multiplyExact(frameSize.width(), frameSize.height()), 3);
        byte[] buffer = new byte[frameBytes];
        Mat frame = new Mat(frameSize.height(), frameSize.width(), CvType.CV_8UC3);
        int frameIndex = 0;
        try (InputStream input = process.getInputStream()) {
            while (readFrame(input, buffer)) {
                frame.put(0, 0, buffer);
                if (!consumer.accept(frameIndex, frame)) break;
                frameIndex++;
            }
        } finally {
            frame.release();
            if (process.isAlive()) process.destroyForcibly();
        }
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        String error = errorOutput.get(30, TimeUnit.SECONDS);
        if (process.exitValue() != 0 && frameIndex == 0) {
            throw new IOException("FFmpeg raw-frame decode failed: " + error);
        }
    }

    private boolean readFrame(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read < 0) return false;
            offset += read;
        }
        return true;
    }

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

    private String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0.0, seconds));
    }

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

    static double contentScore(Mat previousHsv, Mat currentHsv) {
        List<Mat> previousChannels = new ArrayList<>(3);
        List<Mat> currentChannels = new ArrayList<>(3);
        Core.split(previousHsv, previousChannels);
        Core.split(currentHsv, currentChannels);
        double score = 0.0;
        for (int i = 0; i < 3; i++) {
            Mat difference = new Mat();
            Core.absdiff(previousChannels.get(i), currentChannels.get(i), difference);
            score += Core.mean(difference).val[0];
            difference.release();
        }
        previousChannels.forEach(Mat::release);
        currentChannels.forEach(Mat::release);
        return score / 3.0;
    }

    private MatOfPoint2f detectCorners(Mat gray) {
        MatOfPoint corners = new MatOfPoint();
        Mat mask = new Mat();
        Imgproc.goodFeaturesToTrack(gray, corners, OPTICAL_FLOW_MAX_CORNERS,
                OPTICAL_FLOW_QUALITY_LEVEL, OPTICAL_FLOW_MIN_DISTANCE, mask,
                OPTICAL_FLOW_BLOCK_SIZE, false, 0.04);
        MatOfPoint2f points = new MatOfPoint2f(corners.toArray());
        mask.release();
        corners.release();
        return points;
    }

    private TrackedPoints trackedPoints(MatOfPoint2f previous, MatOfPoint2f current, MatOfByte status) {
        Point[] previousArray = previous.toArray();
        Point[] currentArray = current.toArray();
        byte[] statusArray = status.toArray();
        List<Point> validCurrent = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        int count = Math.min(statusArray.length, Math.min(previousArray.length, currentArray.length));
        for (int i = 0; i < count; i++) {
            if (statusArray[i] == 1) {
                validCurrent.add(currentArray[i]);
                double dx = currentArray[i].x - previousArray[i].x;
                double dy = currentArray[i].y - previousArray[i].y;
                distances.add(Math.hypot(dx, dy));
            }
        }
        return new TrackedPoints(validCurrent.toArray(Point[]::new), distances);
    }

    private double median(List<Double> values) {
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }

    private static void loadOpenCv() {
        if (openCvLoaded) return;
        synchronized (VideoService.class) {
            if (!openCvLoaded) {
                OpenCV.loadLocally();
                openCvLoaded = true;
            }
        }
    }

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

    private boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".bmp");
    }

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

    private String toStoragePath(Path path) {
        Path absoluteStorage = storageDir.toAbsolutePath().normalize();
        Path absolutePath = path.toAbsolutePath().normalize();
        try {
            return storageDir.getFileName() + "/" + absoluteStorage.relativize(absolutePath).toString().replace("\\", "/");
        } catch (Exception e) {
            return path.toString().replace("\\", "/");
        }
    }

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

    private double fileSizeMb(Path path) {
        try {
            return Math.round(Files.size(path) / 1024.0 / 1024.0 * 100.0) / 100.0;
        } catch (IOException e) {
            return 0.0;
        }
    }

    private ProcessResult run(List<String> command, long timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(new ArrayList<>(command)).redirectErrorStream(true).start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes());
            } catch (IOException e) {
                return "";
            }
        });
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            output.cancel(true);
            return new ProcessResult(-1, "");
        }
        return new ProcessResult(process.exitValue(), output.join());
    }

    private record TrackedPoints(Point[] current, List<Double> distances) {
    }

    private record FrameSize(int width, int height) {
    }

    @FunctionalInterface
    private interface FrameConsumer {
        boolean accept(int frameIndex, Mat frame) throws Exception;
    }

    private record ProcessResult(int exitCode, String stdout) {
    }
}
