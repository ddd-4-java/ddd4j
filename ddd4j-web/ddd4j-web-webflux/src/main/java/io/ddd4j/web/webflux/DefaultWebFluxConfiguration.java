/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux;

import io.ddd4j.core.ProfileManager;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.RequestIdGenerator;
import io.ddd4j.web.core.health.ReadinessEndpoint;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.webflux.config.LocalResourceProperteis;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import org.springframework.extension.web.server.ReactiveRequestContextFilter;
import org.springframework.extension.web.server.i18n.XHeaderLocaleContextResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.server.i18n.LocaleContextResolver;

import java.util.Locale;
import java.util.List;
import java.util.TimeZone;

@Configuration(proxyBeanMethods = false)
public class DefaultWebFluxConfiguration {

    @Bean
    public ProfileManager profileManager(Environment environment) {
        return new ProfileManager(environment::getActiveProfiles);
    }

    @Bean(name = "ddd4jReactiveRequestContextFilter")
    public ReactiveRequestContextFilter requestContextFilter() {
        return new ReactiveRequestContextFilter();
    }

    @Bean
    public LocaleContextResolver localeContextResolver() {

        XHeaderLocaleContextResolver localeContextResolver = new XHeaderLocaleContextResolver();
        localeContextResolver.setDefaultLocale(Locale.getDefault());
        localeContextResolver.setDefaultTimeZone(TimeZone.getDefault());
        return localeContextResolver;
    }

    @Bean
    public BearerSubjectAuthenticator bearerSubjectAuthenticator() {
        return new BearerSubjectAuthenticator();
    }

    @Bean
    public WebExceptionTranslator webExceptionTranslator() {
        return new DefaultWebExceptionTranslator();
    }

    @Bean
    public WebAccessPolicy webAccessPolicy(Environment environment) {
        String[] publicPaths = environment.getProperty("ddd4j.web.public-paths", String[].class,
                new String[]{"/health", "/health/readiness", "/health/liveness", ReadinessEndpoint.PATH,
                        "/assets/**", "/webjars/**"});
        return new PathWebAccessPolicy(java.util.Arrays.asList(publicPaths), AuthenticationMode.REQUIRED);
    }

    @Bean
    public WebRequestContextFactory webRequestContextFactory(Environment environment) {
        boolean trustForwardedHeaders = environment.getProperty("ddd4j.web.trust-forwarded-headers",
                Boolean.class, false);
        ClientIpResolver clientIpResolver = trustForwardedHeaders
                ? ClientIpResolver.trustedProxy() : ClientIpResolver.remoteAddressOnly();
        return new WebRequestContextFactory(RequestIdGenerator.uuid(), clientIpResolver);
    }

    @Bean
    public WebRequestLifecycle webRequestLifecycle(BearerSubjectAuthenticator authenticator,
                                                   WebAccessPolicy accessPolicy) {
        return new WebRequestLifecycle(authenticator, accessPolicy);
    }

    @Bean
    public WebIdempotencyLifecycle webIdempotencyLifecycle() {
        return new WebIdempotencyLifecycle(new CacheIdempotencyGuard());
    }

    @Bean
    public Ddd4jWebFluxFilter ddd4jWebFluxFilter(WebRequestContextFactory contextFactory,
                                                 WebRequestLifecycle requestLifecycle,
                                                 WebIdempotencyLifecycle idempotencyLifecycle) {
        return new Ddd4jWebFluxFilter(contextFactory, requestLifecycle, idempotencyLifecycle);
    }

    /**
     * 注册不依赖 Spring Boot Actuator 的显式 readiness 端点。
     */
    @Bean
    public Ddd4jWebFluxReadinessController ddd4jWebFluxReadinessController(
            RuntimeReadinessRegistry readinessRegistry) {
        return new Ddd4jWebFluxReadinessController(readinessRegistry);
    }

    @Bean
    public DefaultWebFluxConfigurer defaultWebFluxConfigurer(LocalResourceProperteis localResourceProperteis) {
        return new DefaultWebFluxConfigurer(localResourceProperteis);
    }

}
