package com.envmatch.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

@TableName(value = "task_results", autoResultMap = true)
public class TaskResult {
    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode dimensionScores;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode similarPoints;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode differencePoints;
    
    private String summary;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode keyFramesA;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode keyFramesB;
    
    private String errorMessage;
    private Double inputTokens;
    private Double outputTokens;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public JsonNode getDimensionScores() { return dimensionScores; }
    public void setDimensionScores(JsonNode dimensionScores) { this.dimensionScores = dimensionScores; }
    public JsonNode getSimilarPoints() { return similarPoints; }
    public void setSimilarPoints(JsonNode similarPoints) { this.similarPoints = similarPoints; }
    public JsonNode getDifferencePoints() { return differencePoints; }
    public void setDifferencePoints(JsonNode differencePoints) { this.differencePoints = differencePoints; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public JsonNode getKeyFramesA() { return keyFramesA; }
    public void setKeyFramesA(JsonNode keyFramesA) { this.keyFramesA = keyFramesA; }
    public JsonNode getKeyFramesB() { return keyFramesB; }
    public void setKeyFramesB(JsonNode keyFramesB) { this.keyFramesB = keyFramesB; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Double getInputTokens() { return inputTokens; }
    public void setInputTokens(Double inputTokens) { this.inputTokens = inputTokens; }
    public Double getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Double outputTokens) { this.outputTokens = outputTokens; }
}
