package com.envmatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@MapperScan("com.envmatch.mapper")
@EnableAsync
@SpringBootApplication
public class EnvMatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnvMatchApplication.class, args);
    }
}
