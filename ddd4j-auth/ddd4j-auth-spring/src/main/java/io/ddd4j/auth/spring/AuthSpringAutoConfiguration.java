package io.ddd4j.auth.spring;

import io.ddd4j.auth.spring.satoken.SaTokenExceptionHandler;
import io.ddd4j.auth.spring.shiro.ShiroExceptionHandler;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auth Spring 桥接自动装配。
 *
 * <p>提供两个核心 Bean：
 * <ul>
 *   <li>{@link SubjectRegistrar}：监听 SubjectProvider Bean，写回 SubjectKit（修复注册断链）</li>
 *   <li>{@link SubjectProvider}：按 classpath 自动选择三鉴权实现（sa-token 优先）</li>
 * </ul>
 *
 * <p>三鉴权实现的 {@code @ConditionalOnClass} 互斥，保证同一时刻只有一个 SubjectProvider 生效。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
@Configuration(proxyBeanMethods = false)
public class AuthSpringAutoConfiguration {

    /**
     * Subject 注册器（BeanPostProcessor）：监听 SubjectProvider Bean 写回 SubjectKit。
     */
    @Bean
    @ConditionalOnMissingBean
    public SubjectRegistrar subjectRegistrar() {
        return new SubjectRegistrar();
    }

    // ==================== 三鉴权实现互斥装配（按 classpath 自动选择）====================

    /**
     * sa-token 实现（优先级最高，主推方案）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "cn.dev33.satoken.stp.StpUtil")
    public static class SaTokenAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(SubjectProvider.class)
        public SubjectProvider saTokenSubjectProvider() {
            return new io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider();
        }

        /**
         * Sa-Token 异常处理器（仅 Servlet Web 环境装配）。
         */
        @Bean
        @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
        @ConditionalOnMissingBean(SaTokenExceptionHandler.class)
        public SaTokenExceptionHandler saTokenExceptionHandler() {
            return new SaTokenExceptionHandler();
        }

    }

    /**
     * Shiro 实现（旧项目兼容）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.shiro.SecurityUtils")
    public static class ShiroAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(SubjectProvider.class)
        public SubjectProvider shiroSubjectProvider() {
            return new io.ddd4j.auth.shiro.subject.ShiroSubjectProvider();
        }

        /**
         * Shiro 异常处理器（仅 Servlet Web 环境装配）。
         */
        @Bean
        @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
        @ConditionalOnMissingBean(ShiroExceptionHandler.class)
        public ShiroExceptionHandler shiroExceptionHandler() {
            return new ShiroExceptionHandler();
        }

    }

    // Spring Security 实现的自动装配由 ddd4j-auth-security 模块自身提供
    // （SecuritySubjectProvider 所在模块允许 Spring 依赖）
    // Security 异常处理器同样由 ddd4j-auth-security 模块提供（SecurityExceptionHandler）

}
