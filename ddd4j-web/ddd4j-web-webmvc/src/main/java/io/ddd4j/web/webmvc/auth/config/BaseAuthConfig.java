package io.ddd4j.web.webmvc.auth.config;

import io.ddd4j.web.webmvc.auth.interceptor.BaseAuthWebInterceptor;
import io.ddd4j.web.webmvc.interceptor.BaseWebInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基础认证配置
 *
 * @author Loong Wan
 */
@Configuration
public class BaseAuthConfig {

    @Bean
    public BaseWebInterceptor baseAuthInterceptor() {
        return new BaseAuthWebInterceptor();
    }
}