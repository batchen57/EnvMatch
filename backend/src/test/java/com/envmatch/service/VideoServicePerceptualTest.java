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
}
