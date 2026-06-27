package io.ddd4j.spring.config;

import io.ddd4j.spring.context.SpringContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Spring 核心配置（从 ddd4j-core 迁出至 ddd4j-spring）
 * <p>
 * 注册 {@link SpringContext} 为 Spring Bean，使其不依赖 web 模块也能激活。
 *
 * <p>迁移说明：原 ddd4j-core 模块中的 {@code io.ddd4j.core.config.BaseCoreConfig}
 * 已废弃，请使用本类。
 *
 * @author Loong Wan
 * @since 3.4.x
 */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(exposeProxy = true)
public class SpringCoreConfig {

    @Bean
    public SpringContext springContext() {
        return new SpringContext();
    }
}
