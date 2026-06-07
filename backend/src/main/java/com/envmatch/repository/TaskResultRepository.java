package com.envmatch.repository;

import com.envmatch.model.TaskResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskResultRepository extends JpaRepository<TaskResult, String> {
}
