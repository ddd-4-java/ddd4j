package io.ddd4j.auth.security;

import io.ddd4j.auth.security.subject.SecuritySubjectProvider;
import io.ddd4j.core.subject.SubjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Security 自动装配（本模块允许 Spring 依赖）。
 *
 * <p><b>迁移说明</b>：自 2.0.x 起，本类将从 {@code ddd4j-auth-security} 下移到
 * {@code ddd4j-boot-auth-security-autoconfigure}（Spring Boot starter）。
 *
 * <p>当 classpath 存在 Spring Security 时，自动注册 {@link SecuritySubjectProvider}。
 * 与 ddd4j-auth-spring 的 SaToken/Shiro 装配互斥（通过 @ConditionalOnMissingBean）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 * @deprecated 自 2.0.x 起下移到 {@code ddd4j-boot-auth-security-autoconfigure.SecurityAutoConfiguration}
 */
@Deprecated
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SubjectProvider.class)
    public SubjectProvider securitySubjectProvider() {
        return new SecuritySubjectProvider();
    }

}
