package com.envmatch.repository;

import com.envmatch.model.Task;
import com.envmatch.model.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, String> {
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);
    Page<Task> findByStatusIn(Collection<TaskStatus> statuses, Pageable pageable);
    long countByStatus(TaskStatus status);
    long countByStatusIn(Collection<TaskStatus> statuses);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<Task> findByStatus(TaskStatus status);

    // 公共接口允许任意行偏移量，使用原生 LIMIT/OFFSET 可保持 Python 版语义，并避免加载全部任务。
    @Query(value = """
            SELECT * FROM tasks
            ORDER BY CASE
                WHEN typeof(created_at) IN ('integer', 'real') THEN created_at / 1000.0
                ELSE unixepoch(created_at)
            END DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Task> findPage(@Param("offset") int offset, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM tasks
            WHERE status = :status
            ORDER BY CASE
                WHEN typeof(created_at) IN ('integer', 'real') THEN created_at / 1000.0
                ELSE unixepoch(created_at)
            END DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Task> findPageByStatus(@Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM tasks
            WHERE status IN ('PENDING', 'PROCESSING')
            ORDER BY CASE
                WHEN typeof(created_at) IN ('integer', 'real') THEN created_at / 1000.0
                ELSE unixepoch(created_at)
            END DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Task> findProcessingPage(@Param("offset") int offset, @Param("limit") int limit);
}
