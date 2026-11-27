package io.ddd4j.spring.config;

import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.ddd4j.spring.context.SpringContext;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * \u6c47\u96c6 Spring \u5bb9\u5668\u4e2d\u7684\u5c31\u7eea\u68c0\u67e5\u9879\u3002
     * @param readinessContributors \u5c31\u7eea\u68c0\u67e5\u9879\u63d0\u4f9b\u8005
     * @return \u8fd0\u884c\u65f6\u5c31\u7eea\u6ce8\u518c\u8868
     */
    @Bean
    public RuntimeReadinessRegistry runtimeReadinessRegistry(
            ObjectProvider<ReadinessContributor> readinessContributors) {
        return new RuntimeReadinessRegistry(readinessContributors.orderedStream().toList());
    }

    /**
     * \u521b\u5efa Spring \u4e0a\u4e0b\u6587\u6865\u63a5\u5668\u3002
     * @return Spring \u4e0a\u4e0b\u6587\u6865\u63a5\u5668
     */
    @Bean
    public SpringContext springContext() {
        return new SpringContext();
    }

    /**
     * \u521b\u5efa\u53ef\u611f\u77e5 Spring \u4e0a\u4e0b\u6587\u7684\u6846\u67b6\u4e0a\u4e0b\u6587\u3002
     * @return Spring \u4e0a\u4e0b\u6587\u9002\u914d\u5668
     */
    @Bean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }
}
