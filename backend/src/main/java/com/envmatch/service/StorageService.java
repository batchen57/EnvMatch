package com.envmatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 媒体文件存储服务类。
 * 
 * <p>负责管理系统上传的 A/B 两份原始视频/图片媒体文件以及生成的中间文件、裁剪视频和抽帧预览图片的存储。
 * 在初始化时，会自动创建指定的物理存储根目录。</p>
 */
@Service
public class StorageService {
    
    /** 媒体文件本地存储物理根目录 */
    private final Path storageDir;

    /**
     * 构造函数，初始化存储路径并自动创建相应物理目录。
     *
     * @param storageDir 物理存储目录路径，默认从环境变量或配置 envmatch.storage-dir 获取
     * @throws IOException 目录创建失败时抛出异常
     */
    public StorageService(@Value("${envmatch.storage-dir:storage}") String storageDir) throws IOException {
        this.storageDir = Path.of(storageDir);
        Files.createDirectories(this.storageDir);
    }

    /**
     * 将用户上传的多部分文件（MultipartFile）保存到指定的物理存储路径。
     * 文件将重命名为 "{taskId}_{suffix}.{original_extension}"，例如 "uuid-123_A.mp4"。
     *
     * @param file   用户上传的多部分媒体文件
     * @param taskId 关联的任务 ID 唯一标识
     * @param suffix 后缀区分标识（例如 "A" 或 "B"）
     * @return 保存后的物理文件 Path 对象
     * @throws IOException 文件流复制或物理写入出错时抛出异常
     */
    public Path saveUpload(MultipartFile file, String taskId, String suffix) throws IOException {
        String original = file.getOriginalFilename();
        String ext = ".mp4";
        if (original != null) {
            int idx = original.lastIndexOf('.');
            if (idx >= 0) ext = original.substring(idx);
        }
        Path target = storageDir.resolve(taskId + "_" + suffix + ext).normalize();
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /**
     * 获取媒体存储根目录。
     *
     * @return 存储目录的 Path 对象
     */
    public Path storageDir() {
        return storageDir;
    }
}
