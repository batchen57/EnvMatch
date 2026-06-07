package com.envmatch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @Column(length = 36)
    private String id;
    private String taskName;
    private String videoAPath;
    private String videoBPath;
    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;
    private Double similarityScore;
    private String modelId;
    @Column(columnDefinition = "TEXT")
    private String prompt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double inputTokens;
    private Double outputTokens;
    private Double videoADuration;
    private Double videoBDuration;
    private String videoAResolution;
    private String videoBResolution;
    private Double videoASize;
    private Double videoBSize;
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JsonNode preprocessOptions;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    @JsonProperty("video_a_path")
    public String getVideoAPath() { return videoAPath; }
    public void setVideoAPath(String videoAPath) { this.videoAPath = videoAPath; }
    @JsonProperty("video_b_path")
    public String getVideoBPath() { return videoBPath; }
    public void setVideoBPath(String videoBPath) { this.videoBPath = videoBPath; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double similarityScore) { this.similarityScore = similarityScore; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Double getInputTokens() { return inputTokens; }
    public void setInputTokens(Double inputTokens) { this.inputTokens = inputTokens; }
    public Double getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Double outputTokens) { this.outputTokens = outputTokens; }
    @JsonProperty("video_a_duration")
    public Double getVideoADuration() { return videoADuration; }
    public void setVideoADuration(Double videoADuration) { this.videoADuration = videoADuration; }
    @JsonProperty("video_b_duration")
    public Double getVideoBDuration() { return videoBDuration; }
    public void setVideoBDuration(Double videoBDuration) { this.videoBDuration = videoBDuration; }
    @JsonProperty("video_a_resolution")
    public String getVideoAResolution() { return videoAResolution; }
    public void setVideoAResolution(String videoAResolution) { this.videoAResolution = videoAResolution; }
    @JsonProperty("video_b_resolution")
    public String getVideoBResolution() { return videoBResolution; }
    public void setVideoBResolution(String videoBResolution) { this.videoBResolution = videoBResolution; }
    @JsonProperty("video_a_size")
    public Double getVideoASize() { return videoASize; }
    public void setVideoASize(Double videoASize) { this.videoASize = videoASize; }
    @JsonProperty("video_b_size")
    public Double getVideoBSize() { return videoBSize; }
    public void setVideoBSize(Double videoBSize) { this.videoBSize = videoBSize; }
    public JsonNode getPreprocessOptions() { return preprocessOptions; }
    public void setPreprocessOptions(JsonNode preprocessOptions) { this.preprocessOptions = preprocessOptions; }
}
