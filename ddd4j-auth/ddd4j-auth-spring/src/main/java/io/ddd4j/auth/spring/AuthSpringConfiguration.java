package io.ddd4j.auth.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 鉴权组件的 Spring 桥接配置。
 */
@Configuration(proxyBeanMethods = false)
public class AuthSpringConfiguration {

    /**
     * \u6ce8\u518c\u8ba4\u8bc1\u4e3b\u4f53\u5230 Spring \u8fd0\u884c\u65f6\u3002
     * @return \u4e3b\u4f53\u6ce8\u518c\u5668
     */
    @Bean
    public SubjectRegistrar subjectRegistrar() {
        return new SubjectRegistrar();
    }
}
