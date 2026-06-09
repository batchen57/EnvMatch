package com.envmatch.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.envmatch.model.Task;
import com.envmatch.model.TaskStatus;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务数据库映射层接口。
 * 
 * <p>继承自 MyBatis-Plus {@link BaseMapper}，提供对对比分析任务 of 增删改查支持，
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
    default List<Task> findPage(@Param("offset") int offset, @Param("limit") int limit) {
        return selectList(new LambdaQueryWrapper<Task>()
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset));
    }

    /**
     * 根据特定任务状态分页查询任务列表（按创建时间倒序）。
     *
     * @param status 任务状态名称字符串
     * @param offset 偏移量
     * @param limit  每页限制数量
     * @return 过滤后的任务列表
     */
    default List<Task> findPageByStatus(@Param("status") String status, @Param("offset") int offset, @Param("limit") int limit) {
        return selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, TaskStatus.valueOf(status))
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset));
    }

    /**
     * 分页查询处于“待处理(PENDING)”或“处理中(PROCESSING)”状态的任务列表（用于队列展示或调度监控）。
     *
     * @param offset 偏移量
     * @param limit  每页限制数量
     * @return 处理中的任务列表
     */
    default List<Task> findProcessingPage(@Param("offset") int offset, @Param("limit") int limit) {
        return selectList(new LambdaQueryWrapper<Task>()
                .in(Task::getStatus, List.of(TaskStatus.PENDING, TaskStatus.PROCESSING))
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset));
    }
}
