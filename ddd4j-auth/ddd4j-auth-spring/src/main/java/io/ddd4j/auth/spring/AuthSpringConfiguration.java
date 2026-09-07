package io.ddd4j.auth.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 鉴权组件的 Spring 桥接配置。
 */
@Configuration(proxyBeanMethods = false)
public class AuthSpringConfiguration {

    @Bean
    public SubjectRegistrar subjectRegistrar() {
        return new SubjectRegistrar();
    }
}
