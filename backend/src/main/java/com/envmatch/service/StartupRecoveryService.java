package com.envmatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.envmatch.model.Task;
import com.envmatch.model.TaskResult;
import com.envmatch.model.TaskStatus;
import com.envmatch.mapper.TaskMapper;
import com.envmatch.mapper.TaskResultMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务启动自动恢复与清理服务。
 * 
 * <p>该服务实现 {@link ApplicationRunner} 接口，在 Spring Boot 容器完全启动后自动执行一次。
 * 其核心职责是扫描数据库中处于 {@code PENDING}（等待中）或 {@code PROCESSING}（处理中）状态的遗留任务。
 * 这些状态通常是由服务器非正常关闭、断电重启或发生 OOM 异常导致的任务断流。
 * 服务会将这些受影响的遗留任务统一标记为 {@code FAILED}，并填入相应的故障恢复说明，防止前端界面卡死在“对比中”状态。</p>
 */
@Service
public class StartupRecoveryService implements ApplicationRunner {
    
    /** 任务数据库操作接口 */
    private final TaskMapper taskMapper;
    
    /** 任务结果数据库操作接口 */
    private final TaskResultMapper resultMapper;

    /**
     * 构造函数，自动注入 Mapper 组件。
     *
     * @param taskMapper   任务 Mapper
     * @param resultMapper 任务结果 Mapper
     */
    public StartupRecoveryService(TaskMapper taskMapper, TaskResultMapper resultMapper) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
    }

    /**
     * 系统启动后的回调入口方法。
     * 查找所有残留的未完成任务，将其状态重置为失败并持久化错误日志。
     *
     * @param args 应用程序启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        List<Task> abandoned = new ArrayList<>();
        abandoned.addAll(taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getStatus, TaskStatus.PENDING)));
        abandoned.addAll(taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getStatus, TaskStatus.PROCESSING)));
        if (abandoned.isEmpty()) return;

        for (Task task : abandoned) {
            task.setStatus(TaskStatus.FAILED);
            taskMapper.updateById(task);

            TaskResult result = resultMapper.selectById(task.getId());
            if (result == null) {
                result = new TaskResult();
                result.setTaskId(task.getId());
            }
            String message = "Task was interrupted by a previous server stop or timeout. Please create it again.";
            result.setErrorMessage(message);
            if (result.getSummary() == null || result.getSummary().isBlank()) {
                result.setSummary(message);
            }
            
            if (resultMapper.selectById(result.getTaskId()) == null) {
                resultMapper.insert(result);
            } else {
                resultMapper.updateById(result);
            }
        }
    }
}
