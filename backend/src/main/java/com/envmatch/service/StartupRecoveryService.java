package com.envmatch.service;

import com.envmatch.model.Task;
import com.envmatch.model.TaskResult;
import com.envmatch.model.TaskStatus;
import com.envmatch.repository.TaskRepository;
import com.envmatch.repository.TaskResultRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StartupRecoveryService implements ApplicationRunner {
    private final TaskRepository taskRepository;
    private final TaskResultRepository resultRepository;

    public StartupRecoveryService(TaskRepository taskRepository, TaskResultRepository resultRepository) {
        this.taskRepository = taskRepository;
        this.resultRepository = resultRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Task> abandoned = new ArrayList<>();
        abandoned.addAll(taskRepository.findByStatus(TaskStatus.PENDING));
        abandoned.addAll(taskRepository.findByStatus(TaskStatus.PROCESSING));
        if (abandoned.isEmpty()) return;

        for (Task task : abandoned) {
            task.setStatus(TaskStatus.FAILED);
            taskRepository.save(task);

            TaskResult result = resultRepository.findById(task.getId()).orElseGet(() -> {
                TaskResult r = new TaskResult();
                r.setTaskId(task.getId());
                return r;
            });
            String message = "Task was interrupted by a previous server stop or timeout. Please create it again.";
            result.setErrorMessage(message);
            if (result.getSummary() == null || result.getSummary().isBlank()) {
                result.setSummary(message);
            }
            resultRepository.save(result);
        }
    }
}
