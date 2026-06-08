package com.envmatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.envmatch.model.ModelCallLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模型审计日志数据库映射层接口。
 * 
 * <p>继承自 MyBatis-Plus {@link BaseMapper}，提供模型调用审计日志的持久化功能，
 * 以及多维度搜索与分页查询。</p>
 */
public interface ModelCallLogMapper extends BaseMapper<ModelCallLog> {
    
    /**
     * 分页查询模型调用日志列表（按启动时间倒序）。
     *
     * @param offset 偏移量
     * @param limit  每页限制数量
     * @return 模型调用日志列表
     */
    @Select("SELECT id, task_id, task_name, model_id, model_url, started_at, ended_at, status_code, input_tokens, output_tokens FROM model_call_logs ORDER BY started_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<ModelCallLog> findPage(@Param("offset") int offset, @Param("limit") int limit);

    /**
     * 根据关键词多条件分页搜索模型调用日志。
     * 支持匹配任务名称、任务ID以及模型ID。
     *
     * @param pattern 模糊查询匹配模式（通常带 % 占位符）
     * @param offset  偏移量
     * @param limit   每页限制数量
     * @return 匹配的模型调用日志列表
     */
    @Select("""
            SELECT id, task_id, task_name, model_id, model_url, started_at, ended_at, status_code, input_tokens, output_tokens FROM model_call_logs
            WHERE lower(coalesce(task_name, '')) LIKE lower(#{pattern})
               OR lower(coalesce(task_id, '')) LIKE lower(#{pattern})
               OR lower(coalesce(model_id, '')) LIKE lower(#{pattern})
            ORDER BY started_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ModelCallLog> searchPage(@Param("pattern") String pattern, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 统计多条件模糊搜索的记录总数，用于分页组件。
     *
     * @param pattern 模糊查询匹配模式
     * @return 匹配记录总数
     */
    @Select("""
            SELECT count(*) FROM model_call_logs
            WHERE lower(coalesce(task_name, '')) LIKE lower(#{pattern})
               OR lower(coalesce(task_id, '')) LIKE lower(#{pattern})
               OR lower(coalesce(model_id, '')) LIKE lower(#{pattern})
            """)
    long countSearch(@Param("pattern") String pattern);
}
