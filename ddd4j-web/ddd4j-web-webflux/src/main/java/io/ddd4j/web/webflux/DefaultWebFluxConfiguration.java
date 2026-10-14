/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.webflux;

import io.ddd4j.core.ProfileManager;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.context.RequestIdGenerator;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.core.health.ReadinessEndpoint;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.webflux.config.LocalResourceProperteis;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.extension.web.server.ReactiveRequestContextFilter;
import org.springframework.extension.web.server.i18n.XHeaderLocaleContextResolver;
import org.springframework.web.server.i18n.LocaleContextResolver;

import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

/**
 * WebFlux（响应式 Web）环境的默认 Bean 装配配置。
 *
 * <p>注册请求上下文过滤器、区域解析、认证、异常翻译、访问策略、
 * 幂等生命周期与静态资源映射等 ddd4j Web 核心组件。
 *
 * @since 2.0.x
 */
@Configuration(proxyBeanMethods = false)
public class DefaultWebFluxConfiguration {

    /**
     * 注册 Profile 管理器。
     *
     * @param environment Spring 环境
     * @return Profile 管理器
     */
    @Bean
    public ProfileManager profileManager(Environment environment) {
        return new ProfileManager(environment::getActiveProfiles);
    }

    /**
     * 注册请求上下文过滤器（绑定 requestId/租户等请求级上下文）。
     *
     * @return 响应式请求上下文过滤器
     */
    @Bean(name = "ddd4jReactiveRequestContextFilter")
    public ReactiveRequestContextFilter requestContextFilter() {
        return new ReactiveRequestContextFilter();
    }

    /**
     * 注册基于 X 头的区域（Locale）上下文解析器。
     *
     * @return 区域上下文解析器
     */
    @Bean
    public LocaleContextResolver localeContextResolver() {

        XHeaderLocaleContextResolver localeContextResolver = new XHeaderLocaleContextResolver();
        localeContextResolver.setDefaultLocale(Locale.getDefault());
        localeContextResolver.setDefaultTimeZone(TimeZone.getDefault());
        return localeContextResolver;
    }

    /**
     * 注册 Bearer Token 主体认证器。
     *
     * @return 认证器实例
     */
    @Bean
    public BearerSubjectAuthenticator bearerSubjectAuthenticator() {
        return new BearerSubjectAuthenticator();
    }

    /**
     * 注册 Web 异常翻译器。
     *
     * @return 异常翻译器实例
     */
    @Bean
    public WebExceptionTranslator webExceptionTranslator() {
        return new DefaultWebExceptionTranslator();
    }

    /**
     * 注册基于路径的访问策略（默认放行健康检查与静态资源路径）。
     *
     * @param environment Spring 环境（读取 {@code ddd4j.web.public-paths}）
     * @return 访问策略实例
     */
    @Bean
    public WebAccessPolicy webAccessPolicy(Environment environment) {
        String[] publicPaths = environment.getProperty("ddd4j.web.public-paths", String[].class,
                new String[]{"/health", "/health/readiness", "/health/liveness", ReadinessEndpoint.PATH,
                        "/assets/**", "/webjars/**"});
        return new PathWebAccessPolicy(Arrays.asList(publicPaths), AuthenticationMode.REQUIRED);
    }

    /**
     * 注册请求上下文工厂（生成请求 ID、解析客户端 IP）。
     *
     * @param environment Spring 环境（读取 {@code ddd4j.web.trust-forwarded-headers}）
     * @return 请求上下文工厂
     */
    @Bean
    public WebRequestContextFactory webRequestContextFactory(Environment environment) {
        boolean trustForwardedHeaders = environment.getProperty("ddd4j.web.trust-forwarded-headers",
                Boolean.class, false);
        ClientIpResolver clientIpResolver = trustForwardedHeaders
                ? ClientIpResolver.trustedProxy() : ClientIpResolver.remoteAddressOnly();
        return new WebRequestContextFactory(RequestIdGenerator.uuid(), clientIpResolver);
    }

    /**
     * 注册请求生命周期管理器（认证与访问策略编排）。
     *
     * @param authenticator Bearer Token 主体认证器
     * @param accessPolicy  路径访问策略
     * @return 请求生命周期管理器
     */
    @Bean
    public WebRequestLifecycle webRequestLifecycle(BearerSubjectAuthenticator authenticator,
                                                   WebAccessPolicy accessPolicy) {
        return new WebRequestLifecycle(authenticator, accessPolicy);
    }

    /**
     * 注册幂等生命周期管理器（基于缓存租约）。
     *
     * @return 幂等生命周期管理器
     */
    @Bean
    public WebIdempotencyLifecycle webIdempotencyLifecycle() {
        return new WebIdempotencyLifecycle(new CacheIdempotencyGuard());
    }

    /**
     * 注册 ddd4j WebFlux 核心过滤器（串联上下文、生命周期与幂等）。
     *
     * @param contextFactory     请求上下文工厂
     * @param requestLifecycle   请求生命周期管理器
     * @param idempotencyLifecycle 幂等生命周期管理器
     * @return 核心过滤器实例
     */
    @Bean
    public Ddd4jWebFluxFilter ddd4jWebFluxFilter(WebRequestContextFactory contextFactory,
                                                 WebRequestLifecycle requestLifecycle,
                                                 WebIdempotencyLifecycle idempotencyLifecycle) {
        return new Ddd4jWebFluxFilter(contextFactory, requestLifecycle, idempotencyLifecycle);
    }

    /**
     * 注册不依赖 Spring Boot Actuator 的显式 readiness 端点。
     *
     * @param readinessRegistry 运行时就绪状态注册表
     * @return readiness 控制器
     */
    @Bean
    public Ddd4jWebFluxReadinessController ddd4jWebFluxReadinessController(
            RuntimeReadinessRegistry readinessRegistry) {
        return new Ddd4jWebFluxReadinessController(readinessRegistry);
    }

    /**
     * 注册默认静态资源映射配置器。
     *
     * @param localResourceProperteis 本地静态资源映射配置
     * @return 资源映射配置器
     */
    @Bean
    public DefaultWebFluxConfigurer defaultWebFluxConfigurer(LocalResourceProperteis localResourceProperteis) {
        return new DefaultWebFluxConfigurer(localResourceProperteis);
    }

}
