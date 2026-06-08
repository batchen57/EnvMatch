package com.envmatch.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 任务对比分析详细结果实体类。
 * 
 * <p>该类与 {@link Task} 实体属于一对一关系，任务ID（taskId）作为主键。
 * 存储了多维度环境比对得分、相似特征列表、差异点说明、核心总结、A/B关键帧的路径列表以及错误原因等详细数据。</p>
 */
@TableName(value = "task_results", autoResultMap = true)
public class TaskResult {
    
    /** 关联的任务 ID，作为主键 */
    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;
    
    /** 多维度评分（JSON 结构，包含室内布局、墙面地面、固定家具、门窗、光照环境等细分得分） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode dimensionScores;
    
    /** 比对识别出的相同/相似点证据列表 (JSON 字符串数组) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode similarPoints;
    
    /** 比对识别出的环境差异点描述列表 (JSON 字符串数组) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode differencePoints;
    
    /** AI 针对两个环境的综合比对分析总结 */
    private String summary;
    
    /** 素材 A 抽取的关键帧相对存储路径列表 (JSON 字符串数组，用于前端帧预览和还原) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode keyFramesA;
    
    /** 素材 B 抽取的关键帧相对存储路径列表 (JSON 字符串数组) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode keyFramesB;
    
    /** 分析失败时的详细异常/错误描述信息 */
    private String errorMessage;
    
    /** 本次分析实际消耗的输入 Token 数量 */
    private Double inputTokens;
    
    /** 本次分析实际消耗的输出 Token 数量 */
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
