package com.envmatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor(@Value("${envmatch.task-executor.core-size:2}") int coreSize,
                                 @Value("${envmatch.task-executor.max-size:4}") int maxSize,
                                 @Value("${envmatch.task-executor.queue-capacity:50}") int queueCapacity) {
        int normalizedCoreSize = Math.max(1, coreSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 同时限制工作线程数和排队任务数，避免视频处理耗尽 JVM 线程或加剧 SQLite 锁竞争。
        executor.setCorePoolSize(normalizedCoreSize);
        executor.setMaxPoolSize(Math.max(normalizedCoreSize, maxSize));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setThreadNamePrefix("envmatch-task-");
        // 系统过载时明确拒绝新任务，由控制器转换为 HTTP 503，并补偿清理已保存的文件和数据库记录。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
