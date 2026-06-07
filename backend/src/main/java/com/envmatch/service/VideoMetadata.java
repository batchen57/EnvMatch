package com.envmatch.service;

public record VideoMetadata(double duration, String resolution, double sizeMb, double fps) {
    public static VideoMetadata empty() {
        return new VideoMetadata(0.0, "unknown", 0.0, 0.0);
    }
}
