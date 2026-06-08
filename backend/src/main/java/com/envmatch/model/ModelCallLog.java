package com.envmatch.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 模型调用审计日志实体类。
 * 
 * <p>用于持久化大模型接口调用的详细情况，协助对账、调试以及安全审计。
 * 记录了对应的任务、请求 payload（进行了敏感词/Base64大字段脱敏）、原始响应 body、调用起止时间、网络状态码以及 Token 实际消耗量。</p>
 */
@TableName(value = "model_call_logs", autoResultMap = true)
public class ModelCallLog {
    
    /** 唯一自增主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 所属的对比任务 ID */
    private String taskId;
    
    /** 所属的对比任务名称 */
    private String taskName;
    
    /** 被调用的 AI 模型技术标识 (Model ID) */
    private String modelId;
    
    /** 模型调用的 API Endpoint URL (已对 URL 中的 key 参数进行脱敏) */
    private String modelUrl;
    
    /** 模型请求的参数载荷 Payload (通过 Jackson 处理器序列化为 JSON，已对 base64 视频和长媒体字节流进行截断脱敏) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode requestPayload;
    
    /** 模型响应的 JSON 数据/错误信息 (通过 Jackson 处理器序列化为 JSON，已脱敏大 Base64 数据) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode responseBody;
    
    /** 调用发起时间 */
    private LocalDateTime startedAt;
    
    /** 调用结束时间 */
    private LocalDateTime endedAt;
    
    /** HTTP 状态码或处理状态 (例如 200, 500, PROCESSING 等) */
    private String statusCode;
    
    /** 模型请求输入的 Token 数（从响应解析或估算所得） */
    private Double inputTokens;
    
    /** 模型响应生成的 Token 数 */
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
