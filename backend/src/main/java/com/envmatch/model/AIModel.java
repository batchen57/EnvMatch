package com.envmatch.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 模型配置实体类。
 * 
 * <p>用于存储后台支持调用的各家多模态 AI 模型的端点、Key、提供商、描述及能力配置。</p>
 */
@TableName(value = "ai_models", autoResultMap = true)
public class AIModel {
    
    /** 唯一主键 UUID */
    @TableId(type = IdType.INPUT)
    private String id = UUID.randomUUID().toString();
    
    /** 模型友好显示名称，如 Gemini 2.5 Pro */
    private String name;
    
    /** 模型真实技术标识，如 gemini-2.5-pro, gpt-4o */
    private String identifier;
    
    /** 模型提供商，如 Google, OpenAI, MiniMax, Alibaba */
    private String provider;
    
    /** 调用模型所需的 API Key */
    private String apiKey;
    
    /** 模型基础调用地址 (Base URL) */
    private String baseUrl;
    
    /** 模型能力及适用场景简要描述 */
    private String description;
    
    /** 模型的多模态能力支持（通过 JSON 数组存储，包含 "text", "image", "video" 等） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode capabilities;
    
    /** 是否设为系统的默认模型 ("true" / "false") */
    private String isDefault = "false";
    
    /** 排序权重，用于在前端下拉列表中进行优先级排序 */
    private Double sortOrder = 0.0;
    
    /** 记录创建时间（由 MyBatis-Plus 自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /** 记录更新时间（由 MyBatis-Plus 自动更新填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public JsonNode getCapabilities() { return capabilities; }
    public void setCapabilities(JsonNode capabilities) { this.capabilities = capabilities; }
    public String getIsDefault() { return isDefault; }
    public void setIsDefault(String isDefault) { this.isDefault = isDefault; }
    public Double getSortOrder() { return sortOrder; }
    public void setSortOrder(Double sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
