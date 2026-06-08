package com.envmatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.envmatch.model.TaskResult;

/**
 * 任务分析结果数据库映射层接口。
 * 
 * <p>继承自 MyBatis-Plus {@link BaseMapper}，提供对任务多维度评分、相似差异点明细以及提取关键帧路径等结果数据的持久化存储操作。</p>
 */
public interface TaskResultMapper extends BaseMapper<TaskResult> {
}
