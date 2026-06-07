package com.envmatch.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "model_call_logs")
public class ModelCallLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String taskId;
    private String taskName;
    private String modelId;
    @Column(columnDefinition = "TEXT")
    private String modelUrl;
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JsonNode requestPayload;
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JsonNode responseBody;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String statusCode;
    private Double inputTokens;
    private Double outputTokens;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getModelUrl() { return modelUrl; }
    public void setModelUrl(String modelUrl) { this.modelUrl = modelUrl; }
    public JsonNode getRequestPayload() { return requestPayload; }
    public void setRequestPayload(JsonNode requestPayload) { this.requestPayload = requestPayload; }
    public JsonNode getResponseBody() { return responseBody; }
    public void setResponseBody(JsonNode responseBody) { this.responseBody = responseBody; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public Double getInputTokens() { return inputTokens; }
    public void setInputTokens(Double inputTokens) { this.inputTokens = inputTokens; }
    public Double getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Double outputTokens) { this.outputTokens = outputTokens; }
}
