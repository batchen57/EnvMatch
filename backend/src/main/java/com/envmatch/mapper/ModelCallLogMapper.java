package com.envmatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.envmatch.model.ModelCallLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ModelCallLogMapper extends BaseMapper<ModelCallLog> {
    @Select("SELECT id, task_id, task_name, model_id, model_url, started_at, ended_at, status_code, input_tokens, output_tokens FROM model_call_logs ORDER BY started_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<ModelCallLog> findPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT id, task_id, task_name, model_id, model_url, started_at, ended_at, status_code, input_tokens, output_tokens FROM model_call_logs
            WHERE lower(coalesce(task_name, '')) LIKE lower(#{pattern})
               OR lower(coalesce(task_id, '')) LIKE lower(#{pattern})
               OR lower(coalesce(model_id, '')) LIKE lower(#{pattern})
            ORDER BY started_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ModelCallLog> searchPage(@Param("pattern") String pattern, @Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT count(*) FROM model_call_logs
            WHERE lower(coalesce(task_name, '')) LIKE lower(#{pattern})
               OR lower(coalesce(task_id, '')) LIKE lower(#{pattern})
               OR lower(coalesce(model_id, '')) LIKE lower(#{pattern})
            """)
    long countSearch(@Param("pattern") String pattern);
}
