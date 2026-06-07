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

@Service
public class StartupRecoveryService implements ApplicationRunner {
    private final TaskMapper taskMapper;
    private final TaskResultMapper resultMapper;

    public StartupRecoveryService(TaskMapper taskMapper, TaskResultMapper resultMapper) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
    }

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
