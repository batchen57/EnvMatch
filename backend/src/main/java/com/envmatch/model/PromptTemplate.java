package com.envmatch.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 提示词模板实体类。
 * 
 * <p>存储系统内置及用户自定义的环境对比分析 Prompt 模板，用于大模型多模态比对任务的生成控制。</p>
 */
@TableName("prompt_templates")
public class PromptTemplate {
    
    /** 唯一主键 UUID */
    @TableId(type = IdType.INPUT)
    private String id = UUID.randomUUID().toString();
    
    /** 模板名称（例如：系统默认通用提示词，反欺诈中介环境对比专用） */
    private String name;
    
    /** 提示词正文具体内容，包含对 AI 的分析原则和期望的 JSON 输出结构 */
    private String content;
    
    /** 创建时间（由 MyBatis-Plus 自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /** 更新时间（由 MyBatis-Plus 自动更新填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
