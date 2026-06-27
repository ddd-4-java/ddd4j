package io.ddd4j.web.auth.config;

import io.ddd4j.web.auth.interceptor.BaseAuthWebInterceptor;
import io.ddd4j.web.interceptor.BaseWebInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基础认证配置
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 */
@Configuration
public class BaseAuthConfig {

    @Bean
    public BaseWebInterceptor baseAuthInterceptor() {
        return new BaseAuthWebInterceptor();
    }
}