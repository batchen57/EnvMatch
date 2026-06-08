package com.envmatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务执行器配置类。
 * 
 * <p>该类配置了一个有界的自定义线程池 {@link ThreadPoolTaskExecutor}，用于处理高负载的异步任务（如视频抽帧、模型调用等）。
 * 限制最大线程数和排队容量，能够防止视频处理时耗尽 JVM 的系统线程与内存资源，同时降低数据库连接锁的竞争。</p>
 */
@Configuration
public class AsyncConfig {

    /**
     * 创建并配置名为 "taskExecutor" 的异步任务执行线程池。
     *
     * @param coreSize      核心线程数，默认为 2
     * @param maxSize       最大并发线程数，默认为 4
     * @param queueCapacity 任务排队缓存队列容量，默认为 50
     * @return 配置好的 Executor 实例
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor(@Value("${envmatch.task-executor.core-size:2}") int coreSize,
                                 @Value("${envmatch.task-executor.max-size:4}") int maxSize,
                                 @Value("${envmatch.task-executor.queue-capacity:50}") int queueCapacity) {
        int normalizedCoreSize = Math.max(1, coreSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 同时限制工作线程数和排队任务数，避免视频处理耗尽 JVM 线程或加剧数据库连接与锁竞争。
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
