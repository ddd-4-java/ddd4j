package io.ddd4j.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import io.ddd4j.spring.context.SpringContext;

/**
 * 核心 Spring 配置（纯 Spring Framework，不依赖 Spring Boot 自动配置）。
 *
 * <p>注册 {@link SpringContext} 为 Spring Bean，使其不依赖 web 模块也能激活。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(exposeProxy = true)
public class BaseCoreConfig {

    /**
     * 注册 SpringContext，使其在任何模块中都能通过静态方法获取 ApplicationContext。
     */
    @Bean
    public SpringContext springContext() {
        return new SpringContext();
    }

}
