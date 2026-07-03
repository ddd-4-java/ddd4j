package io.ddd4j.data.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ddd4j Data Spring 桥接配置：注册 {@link RepositoryBeanPostProcessor}。
 *
 * <p>本类仅包含 Spring {@code @Configuration}（非 Spring Boot auto-config）。
 * Spring Boot auto-config 在 {@code ddd4j-boot-data} 模块。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jDataSpringConfiguration {

    /**
     * 注册 {@link RepositoryBeanPostProcessor} Bean，用于扫描 {@code BaseRepositoryImpl} 子类
     * 并自动注入 mapper 和配置。
     *
     * @return RepositoryBeanPostProcessor 实例
     */
    @Bean
    public RepositoryBeanPostProcessor repositoryBeanPostProcessor() {
        return new RepositoryBeanPostProcessor();
    }

}
