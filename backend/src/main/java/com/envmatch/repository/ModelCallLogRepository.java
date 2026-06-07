package com.envmatch.repository;

import com.envmatch.model.ModelCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ModelCallLogRepository extends JpaRepository<ModelCallLog, Long> {
    Page<ModelCallLog> findByTaskNameContainingIgnoreCaseOrTaskIdContainingIgnoreCaseOrModelIdContainingIgnoreCase(
            String taskName, String taskId, String modelId, Pageable pageable);

    // Pageable 基于页码，无法准确表达非整页偏移；以下查询用于严格实现接口的 skip/limit 约定。
    @Query(value = """
            SELECT * FROM model_call_logs
            ORDER BY CASE
                WHEN typeof(started_at) IN ('integer', 'real') THEN started_at / 1000.0
                ELSE unixepoch(started_at)
            END DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ModelCallLog> findPage(@Param("offset") int offset, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM model_call_logs
            WHERE lower(coalesce(task_name, '')) LIKE lower(:pattern)
               OR lower(coalesce(task_id, '')) LIKE lower(:pattern)
               OR lower(coalesce(model_id, '')) LIKE lower(:pattern)
            ORDER BY CASE
                WHEN typeof(started_at) IN ('integer', 'real') THEN started_at / 1000.0
                ELSE unixepoch(started_at)
            END DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ModelCallLog> searchPage(@Param("pattern") String pattern, @Param("offset") int offset, @Param("limit") int limit);

    @Query(value = """
            SELECT count(*) FROM model_call_logs
            WHERE lower(coalesce(task_name, '')) LIKE lower(:pattern)
               OR lower(coalesce(task_id, '')) LIKE lower(:pattern)
               OR lower(coalesce(model_id, '')) LIKE lower(:pattern)
            """, nativeQuery = true)
    long countSearch(@Param("pattern") String pattern);
}
