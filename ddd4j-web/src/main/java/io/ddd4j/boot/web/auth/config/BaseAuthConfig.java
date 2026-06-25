package io.ddd4j.boot.web.auth.config;

import io.ddd4j.boot.web.auth.interceptor.BaseAuthWebInterceptor;
import io.ddd4j.boot.web.interceptor.BaseWebInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基础认证配置
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Configuration
public class BaseAuthConfig {

    @Bean
    // @ConditionalOnMissingBean
    public BaseWebInterceptor baseAuthInterceptor() {
        return new BaseAuthWebInterceptor();
    }
}