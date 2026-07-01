package io.ddd4j.spring.config;

import io.ddd4j.spring.context.SpringContext;
import org.springframework.biz.context.SpringContextAwareContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Spring 核心配置
 * <p>
 * 注册 {@link SpringContext} 为 Spring Bean，使其不依赖 web 模块也能激活。
 *
 * <p>迁移说明：原 ddd4j-core 模块中的 {@code io.ddd4j.core.config.BaseCoreConfig}
 * 已废弃，请使用本类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(exposeProxy = true)
public class SpringCoreConfig {

    @Bean
    public SpringContext springContext() {
        return new SpringContext();
    }

    @Bean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }
}
