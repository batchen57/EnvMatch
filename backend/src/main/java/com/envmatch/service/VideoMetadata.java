package com.envmatch.service;

/**
 * 视频素材元数据 Record 记录类。
 * 
 * <p>用于封装探测出的视频时长、分辨率、文件大小及帧率（FPS），便于在抽帧和比对服务中流转。</p>
 * 
 * @param duration   视频总时长（秒），如果是图片则为 0.0
 * @param resolution 视频分辨率字符串 (例如 "1920x1080")，如果不可读则为 "unknown"
 * @param sizeMb     视频文件大小（MB）
 * @param fps        视频帧率（FPS）
 */
public record VideoMetadata(double duration, String resolution, double sizeMb, double fps) {
    
    /**
     * 获取表示未知/为空状态的默认元数据。
     *
     * @return 默认的 VideoMetadata 实例
     */
    public static VideoMetadata empty() {
        return new VideoMetadata(0.0, "unknown", 0.0, 0.0);
    }
}
