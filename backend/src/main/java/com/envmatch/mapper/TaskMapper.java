package com.envmatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.envmatch.model.Task;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务数据库映射层接口。
 * 
 * <p>继承自 MyBatis-Plus {@link BaseMapper}，提供对对比分析任务的增删改查支持，
 * 并包含针对任务不同状态的分页联合查询方法。</p>
 */
public interface TaskMapper extends BaseMapper<Task> {
    
    /**
     * 分页查询所有任务列表（按创建时间倒序）。
     *
     * @param offset 偏移量
     * @param limit  每页限制数量
     * @return 任务列表
     */
    @Select("SELECT * FROM tasks ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Task> findPage(@Param("offset") int offset, @Param("limit") int limit);

    /**
     * 根据特定任务状态分页查询任务列表（按创建时间倒序）。
     *
     * @param status 任务状态名称字符串
     * @param offset 偏移量
     * @param limit  每页限制数量
     * @return 过滤后的任务列表
     */
    @Select("SELECT * FROM tasks WHERE status = #{status} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Task> findPageByStatus(@Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 分页查询处于“待处理(PENDING)”或“处理中(PROCESSING)”状态的任务列表（用于队列展示或调度监控）。
     *
     * @param offset 偏移量
     * @param limit  每页限制数量
     * @return 处理中的任务列表
     */
    @Select("SELECT * FROM tasks WHERE status IN ('PENDING', 'PROCESSING') ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Task> findProcessingPage(@Param("offset") int offset, @Param("limit") int limit);
}
