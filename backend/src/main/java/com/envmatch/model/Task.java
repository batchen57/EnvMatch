package com.envmatch.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 视频环境相似度对比分析任务实体类。
 * 
 * <p>该类存储了由用户发起的比对任务的主要元数据，包括上传的 A/B 两个视频路径、
 * 所选模型、当前的执行状态、分析出的相似度得分、Token 消耗量以及两份视频素材的元数据信息（时长、分辨率、文件大小、FPS）。</p>
 */
@TableName(value = "tasks", autoResultMap = true)
public class Task {
    
    /** 唯一主键 UUID */
    @TableId(type = IdType.INPUT)
    private String id = UUID.randomUUID().toString();
    
    /** 任务名称，由用户在上传时指定 */
    private String taskName;
    
    /** 视频/图片素材 A 的本地存储路径 */
    private String videoAPath;
    
    /** 视频/图片素材 B 的本地存储路径 */
    private String videoBPath;
    
    /** 任务执行状态，默认为 PENDING (排队中) */
    private TaskStatus status = TaskStatus.PENDING;
    
    /** 核心相似度百分比得分 (0.0 - 100.0) */
    private Double similarityScore;
    
    /** 用于本次比对任务的 AI 模型 ID 标识 */
    private String modelId;
    
    /** 本次比对任务实际使用的系统 Prompt */
    private String prompt;
    
    /** 任务创建时间（由 MyBatis-Plus 自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /** 任务更新时间（由 MyBatis-Plus 自动更新填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    /** 本次任务消耗的输入 Token 数量 */
    private Double inputTokens;
    
    /** 本次任务消耗的输出 Token 数量 */
    private Double outputTokens;
    
    /** 素材 A 视频的总时长（秒，如果是图片则为 0.0） */
    private Double videoADuration;
    
    /** 素材 B 视频的总时长（秒，如果是图片则为 0.0） */
    private Double videoBDuration;
    
    /** 素材 A 的原始分辨率或抽取帧的代表分辨率 (例如 "1920x1080") */
    private String videoAResolution;
    
    /** 素材 B 的原始分辨率或抽取帧的代表分辨率 */
    private String videoBResolution;
    
    /** 素材 A 视频的文件大小（MB） */
    private Double videoASize;
    
    /** 素材 B 视频的文件大小（MB） */
    private Double videoBSize;
    
    /** 图像/视频处理选项（JSON 结构），指定识别模式 (image/video)、采样类型 (fixed/perceptual)、目标分辨率以及裁剪范围 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode preprocessOptions;

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
