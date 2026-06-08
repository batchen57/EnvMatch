package com.envmatch.web.dto;

/**
 * 提示词模板创建/更新 HTTP 请求传输 DTO 对象。
 *
 * @param name    模板名称
 * @param content 提示词正文内容
 */
public record PromptTemplateRequest(
        String name,
        String content
) {
}
