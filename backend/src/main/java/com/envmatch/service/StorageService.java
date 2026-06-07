package com.envmatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class StorageService {
    private final Path storageDir;

    public StorageService(@Value("${envmatch.storage-dir:storage}") String storageDir) throws IOException {
        this.storageDir = Path.of(storageDir);
        Files.createDirectories(this.storageDir);
    }

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

    public Path storageDir() {
        return storageDir;
    }
}
