package com.envmatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.envmatch.model.Task;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TaskMapper extends BaseMapper<Task> {
    @Select("SELECT * FROM tasks ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Task> findPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT * FROM tasks WHERE status = #{status} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Task> findPageByStatus(@Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT * FROM tasks WHERE status IN ('PENDING', 'PROCESSING') ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Task> findProcessingPage(@Param("offset") int offset, @Param("limit") int limit);
}
