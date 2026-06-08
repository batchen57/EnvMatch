package com.envmatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Web MVC 配置类。
 * 
 * <p>提供 Spring MVC 的全局自定义配置，主要包括：
 * <ul>
 *   <li>CORS 全局跨域映射，使得前端页面能够跨域调用接口。</li>
 *   <li>静态资源映射，将本地的上传/处理素材路径映射为 Web 可访问的 URL 路径 "/storage/**"。</li>
 * </ul>
 * </p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    /** 本地媒体素材存储目录 */
    @Value("${envmatch.storage-dir:storage}")
    private String storageDir;

    /**
     * 配置全局跨域设置。
     * 支持所有路径、所有请求源、所有请求方法和头的跨域访问，但不允许携带凭证。
     *
     * @param registry 跨域注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(false);
    }

    /**
     * 配置静态资源路径映射。
     * 将物理存储目录转化为 URL 格式，以提供静态素材预览功能。
     *
     * @param registry 静态资源注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(storageDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/storage/**").addResourceLocations(location);
    }
}
