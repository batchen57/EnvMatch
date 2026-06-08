package com.envmatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * EnvMatch 应用启动入口类。
 * 
 * <p>该类配置了 Spring Boot 启动环境，并启用了以下核心功能：
 * <ul>
 *   <li>{@link MapperScan}: 扫描并注册 MyBatis-Plus 的 Mapper 接口，使得数据访问接口能自动注入到 Spring 容器中。</li>
 *   <li>{@link EnableAsync}: 启用 Spring 异步任务执行机制，支持视频特征处理及 AI 分析的后台异步调用。</li>
 * </ul>
 * </p>
 */
@MapperScan("com.envmatch.mapper")
@EnableAsync
@SpringBootApplication
public class EnvMatchApplication {
    
    /**
     * 应用程序的主入口方法。
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(EnvMatchApplication.class, args);
    }
}
