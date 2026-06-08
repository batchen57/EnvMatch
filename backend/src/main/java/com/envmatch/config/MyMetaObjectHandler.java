package com.envmatch.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充公共字段配置处理器。
 * 
 * <p>用于在插入或更新数据库记录时，自动填充统一审计字段，如创建时间（createdAt）和更新时间（updatedAt），
 * 避免手动为每个实体设置这些属性的值。</p>
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    
    /**
     * 插入记录时的填充逻辑。
     * 自动在新增的实体中填入当前系统时间作为创建时间（createdAt）和更新时间（updatedAt）。
     *
     * @param metaObject 包含实体字段元信息的 MetaObject 对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新记录时的填充逻辑。
     * 自动在更新的实体中将当前系统时间覆盖作为更新时间（updatedAt）。
     *
     * @param metaObject 包含实体字段元信息的 MetaObject 对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
