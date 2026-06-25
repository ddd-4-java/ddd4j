package io.ddd4j.boot.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import io.ddd4j.boot.core.context.SpringContext;

/**
 * 核心自动配置。
 *
 * <p>注册 {@link SpringContext} 为 Spring Bean，使其不依赖 web 模块也能激活。
 * SpringContext 实现了 {@link org.springframework.context.ApplicationContextAware}
 * 和 {@link org.springframework.boot.ApplicationRunner}，
 * 用于全局获取 ApplicationContext 和 Bean。
 *
 * @author wandl
 * @since 3.4.x
 */
@AutoConfiguration
@EnableAspectJAutoProxy(exposeProxy = true)
public class BaseCoreConfig {

    /**
     * 注册 SpringContext，使其在任何模块中都能通过静态方法获取 ApplicationContext。
     */
    @Bean
    @ConditionalOnMissingBean(SpringContext.class)
    public SpringContext springContext() {
        return new SpringContext();
    }

}
