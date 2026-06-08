package com.envmatch.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * AI 模型环境比对分析响应 Record 记录类。
 * 
 * <p>用于封装大模型调用返回的数据，包括提取出的结构化 JSON 对比评分结果、Token 真实/估计用量以及调用发生错误时的异常描述信息。</p>
 * 
 * @param result AI 对比分析的核心结果节点（包括 similarity_score, dimension_scores, similar_points 等）
 * @param usage  Token 使用用量节点（包括 prompt_tokens, completion_tokens, total_tokens 等）
 * @param error  调用失败时的错误原因，成功时为 null
 */
public record AiAnalysisResponse(JsonNode result, JsonNode usage, String error) {
}
