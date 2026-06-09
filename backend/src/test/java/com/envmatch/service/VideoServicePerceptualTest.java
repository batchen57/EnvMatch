package com.envmatch.service;

import org.junit.jupiter.api.Test;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Scalar;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;

import java.util.stream.IntStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoServicePerceptualTest {
    @Test
    void keepsChronologicalCoverageWithinContractLimit() {
        List<Integer> candidates = IntStream.range(0, 48).boxed().toList();

        List<Integer> selected = VideoService.reduceCandidateIndices(candidates, 20);

        assertThat(selected).isSorted();
        assertThat(selected).doesNotHaveDuplicates();
        assertThat(selected).hasSize(20);
        assertThat(selected.get(0)).isZero();
        assertThat(selected.get(selected.size() - 1)).isEqualTo(47);
    }

    @Test
    void keepsAllCandidatesWhenAlreadyWithinLimit() {
        assertThat(VideoService.reduceCandidateIndices(List.of(0, 15, 30), 20))
                .containsExactly(0, 15, 30);
    }

    @Test
    void sceneContentScoreDetectsAbruptVisualChange() {
        Loader.load(org.bytedeco.javacpp.opencv_core.class);
        Mat black = new Mat(36, 64, CV_8UC3, new Scalar(0.0, 0.0, 0.0, 0.0));
        Mat red = new Mat(36, 64, CV_8UC3, new Scalar(0.0, 0.0, 255.0, 0.0));
        Mat blackHsv = new Mat();
        Mat redHsv = new Mat();
        cvtColor(black, blackHsv, COLOR_BGR2HSV);
        cvtColor(red, redHsv, COLOR_BGR2HSV);

        double unchanged = VideoService.contentScore(blackHsv, blackHsv);
        double changed = VideoService.contentScore(blackHsv, redHsv);

        black.release();
        red.release();
        blackHsv.release();
        redHsv.release();
        assertThat(unchanged).isZero();
        assertThat(changed).isGreaterThan(15.0);
    }

    @Test
    void testActualVideosPerceptualSampling() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        VideoService videoService = new VideoService("target/test-storage", mapper);
        
        String[] videoFiles = {"A.mp4", "B.mp4", "C.mp4", "D.mp4", "E.mp4"};
        
        System.out.println("==================== PERCEPTUAL SAMPLING TEST RESULTS ====================");
        for (String file : videoFiles) {
            java.nio.file.Path videoPath = java.nio.file.Path.of("..", file);
            if (!java.nio.file.Files.exists(videoPath)) {
                System.out.println("File not found: " + videoPath.toAbsolutePath());
                continue;
            }
            
            VideoMetadata meta = videoService.metadata(videoPath.toString());
            System.out.printf("Video: %s | Duration: %.2fs | Resolution: %s | FPS: %.2f%n", 
                file, meta.duration(), meta.resolution(), meta.fps());
            
            // 1. Default task range (0s to 15s)
            List<String> framesDefault = videoService.extractFrames(
                videoPath.toString(), 
                "test-" + file.replace(".", "-"), 
                "default", 
                1, 
                720, 
                "perceptual", 
                0.0, 
                15.0
            );
            System.out.printf("  - Default interval [0s, 15s] perceptual frame count: %d%n", framesDefault.size());
        }
        System.out.println("=========================================================================");
    }
}

