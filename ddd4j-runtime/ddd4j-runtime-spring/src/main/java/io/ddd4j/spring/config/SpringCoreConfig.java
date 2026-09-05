package io.ddd4j.spring.config;

import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.ddd4j.spring.context.SpringContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.biz.context.SpringContextAwareContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.stream.Collectors;

/**
 * Spring 核心配置
 * <p>
 * 注册 {@link SpringContext} 为 Spring Bean，使其不依赖 web 模块也能激活。
 *
 * @author wandl
 * @since 1.0.x
 */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(exposeProxy = true)
public class SpringCoreConfig {

    @Bean
    public RuntimeReadinessRegistry runtimeReadinessRegistry(
            ObjectProvider<ReadinessContributor> readinessContributors) {
        return new RuntimeReadinessRegistry(readinessContributors.orderedStream().collect(Collectors.toList()));
    }

    @Bean
    public SpringContext springContext() {
        return new SpringContext();
    }

    @Bean
    public SpringContextAwareContext springContextAwareContext() {
        return new SpringContextAwareContext();
    }
}
