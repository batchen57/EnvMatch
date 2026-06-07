package com.envmatch.service;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

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
        OpenCV.loadLocally();
        Mat black = new Mat(36, 64, CvType.CV_8UC3, new Scalar(0, 0, 0));
        Mat red = new Mat(36, 64, CvType.CV_8UC3, new Scalar(0, 0, 255));
        Mat blackHsv = new Mat();
        Mat redHsv = new Mat();
        Imgproc.cvtColor(black, blackHsv, Imgproc.COLOR_BGR2HSV);
        Imgproc.cvtColor(red, redHsv, Imgproc.COLOR_BGR2HSV);

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
