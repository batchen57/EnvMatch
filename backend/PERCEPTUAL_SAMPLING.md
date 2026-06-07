# Perceptual Sampling Contract

The Java implementation mirrors the Python SceneDetect plus sparse optical-flow sampler.

## Algorithm

1. FFmpeg streams raw BGR frames to Java without an intermediate JPEG candidate pass. OpenCV
   performs scene detection and optical-flow analysis on those decoded frames. FFmpeg seeks to the
   configured `clip_start_seconds` and stops after `clip_end_seconds - clip_start_seconds`.
2. Scene changes use the mean absolute HSV channel difference, matching SceneDetect's default
   content score. Cuts require a score greater than 15.0 and at least 15 frames since the last cut.
3. Sparse optical flow samples at approximately 4 FPS:
   - `goodFeaturesToTrack`: 100 corners, quality 0.3, minimum distance 7, block size 7;
   - pyramidal Lucas-Kanade tracks the selected points;
   - median point displacement is accumulated;
   - a frame is retained when accumulated motion exceeds 5% of frame width;
   - feature points are refreshed every 2 seconds.
4. Scene and optical-flow candidates are merged with the first and last frames.
5. Results are reduced deterministically to at most 20 frames while preserving temporal coverage.
6. If perceptual sampling fails, fixed-FPS extraction is used.
7. If fewer than five video frames are available, evenly distributed fallback frames are added.

## Compatibility Expectations

- Exact frame timestamps can differ at threshold boundaries because decoder and OpenCV versions vary.
- The output must be chronological, contain no duplicates, and contain no more than 20 perceptual frames.
- Image inputs produce one normalized JPEG frame.
- The default analysis interval is 0 to 15 seconds. Fixed sampling, perceptual sampling, fallback
  frames, and native video-model payloads all use the same interval.
- Changes to detector parameters or the maximum frame count require updating regression tests and this contract.
