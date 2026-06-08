package com.envmatch.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * AI 模型配置创建/更新 HTTP 请求传输 DTO 对象。
 *
 * @param name         模型显示名称（如 "GPT-4o"）
 * @param identifier   模型技术标识符（如 "gpt-4o"）
 * @param provider     模型供应商（如 "OpenAI"）
 * @param apiKey       访问令牌 Key
 * @param baseUrl      接口基础 URL
 * @param description  模型简要描述
 * @param capabilities 模型多模态能力集 (JSON 数组)
 * @param isDefault    是否设为默认模型
 * @param sortOrder    排序权重
 */
public record AIModelRequest(
        String name,
        String identifier,
        String provider,
        String apiKey,
        String baseUrl,
        String description,
        JsonNode capabilities,
        String isDefault,
        Double sortOrder
) {
    /**
     * 获取 API Key，如果为 null 则返回空字符串。
     */
    public String apiKeyOrEmpty() {
        return apiKey == null ? "" : apiKey;
    }

    /**
     * 获取基础 URL，如果为 null 则返回空字符串。
     */
    public String baseUrlOrEmpty() {
        return baseUrl == null ? "" : baseUrl;
    }

    /**
     * 获取描述信息，如果为 null 则返回空字符串。
     */
    public String descriptionOrEmpty() {
        return description == null ? "" : description;
    }

    /**
     * 获取是否为默认配置的标识，如果为 null 则返回 "false"。
     */
    public String isDefaultOrFalse() {
        return isDefault == null ? "false" : isDefault;
    }

    /**
     * 获取排序权重值，如果为 null 则返回 0.0。
     */
    public double sortOrderOrZero() {
        return sortOrder == null ? 0.0 : sortOrder;
    }
}
