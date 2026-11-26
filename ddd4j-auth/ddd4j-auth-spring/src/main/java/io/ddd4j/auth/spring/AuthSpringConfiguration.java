package io.ddd4j.auth.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 鉴权组件的 Spring 桥接配置。
 */
@Configuration(proxyBeanMethods = false)
public class AuthSpringConfiguration {

    /**
     * subjectRegistrar() 方法。
     * @return 方法执行结果。
     */
    @Bean
    public SubjectRegistrar subjectRegistrar() {
        return new SubjectRegistrar();
    }
}
