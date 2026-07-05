package io.ddd4j.spring.subject;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auth Spring bridge configuration.
 *
 * <p>This module only provides regular Spring integration. Spring Boot conditional
 * auto-configuration belongs to ddd4j-boot-auth modules.
 */
@Configuration(proxyBeanMethods = false)
public class AuthConfiguration {

    /**
     * Registers SubjectProvider beans back into SubjectKit after initialization.
     */
    @Bean
    public SubjectRegistrar subjectRegistrar() {
        return new SubjectRegistrar();
    }
}
