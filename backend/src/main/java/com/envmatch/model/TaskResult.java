package com.envmatch.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_results")
public class TaskResult {
    @Id
    @Column(length = 36)
    private String taskId;
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JsonNode dimensionScores;
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JsonNode similarPoints;
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JsonNode differencePoints;
    @Column(columnDefinition = "TEXT")
    private String summary;
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JsonNode keyFramesA;
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JsonNode keyFramesB;
    @Column(columnDefinition = "TEXT")
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
