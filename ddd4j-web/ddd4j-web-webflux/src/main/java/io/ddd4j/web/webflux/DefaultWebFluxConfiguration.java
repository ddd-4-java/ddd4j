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

/**
 * WebFlux\uff08\u54cd\u5e94\u5f0f Web\uff09\u73af\u5883\u7684\u9ed8\u8ba4 Bean \u88c5\u914d\u914d\u7f6e\u3002
 *
 *  <p>\u6ce8\u518c\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8fc7\u6ee4\u5668\u3001\u533a\u57df\u89e3\u6790\u3001\u8ba4\u8bc1\u3001\u5f02\u5e38\u7ffb\u8bd1\u3001\u8bbf\u95ee\u7b56\u7565\u3001
 *  \u5e42\u7b49\u751f\u547d\u5468\u671f\u4e0e\u9759\u6001\u8d44\u6e90\u6620\u5c04\u7b49 ddd4j Web \u6838\u5fc3\u7ec4\u4ef6\u3002
 */
@Configuration(proxyBeanMethods = false)
public class DefaultWebFluxConfiguration {

    /**
     * \u6ce8\u518c Profile \u7ba1\u7406\u5668\u3002
     * @param environment Spring \u73af\u5883
     * @return Profile \u7ba1\u7406\u5668
     */
    @Bean
    public ProfileManager profileManager(Environment environment) {
        return new ProfileManager(environment::getActiveProfiles);
    }

    /**
     * \u6ce8\u518c\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8fc7\u6ee4\u5668\uff08\u7ed1\u5b9a requestId/\u79df\u6237\u7b49\u8bf7\u6c42\u7ea7\u4e0a\u4e0b\u6587\uff09\u3002
     * @return \u54cd\u5e94\u5f0f\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8fc7\u6ee4\u5668
     */
    @Bean(name = "ddd4jReactiveRequestContextFilter")
    public ReactiveRequestContextFilter requestContextFilter() {
        return new ReactiveRequestContextFilter();
    }

    /**
     * \u6ce8\u518c\u57fa\u4e8e X \u5934\u7684\u533a\u57df\uff08Locale\uff09\u4e0a\u4e0b\u6587\u89e3\u6790\u5668\u3002
     * @return \u533a\u57df\u4e0a\u4e0b\u6587\u89e3\u6790\u5668
     */
    @Bean
    public LocaleContextResolver localeContextResolver() {

        XHeaderLocaleContextResolver localeContextResolver = new XHeaderLocaleContextResolver();
        localeContextResolver.setDefaultLocale(Locale.getDefault());
        localeContextResolver.setDefaultTimeZone(TimeZone.getDefault());
        return localeContextResolver;
    }

    /**
     * \u6ce8\u518c Bearer Token \u4e3b\u4f53\u8ba4\u8bc1\u5668\u3002
     * @return \u8ba4\u8bc1\u5668\u5b9e\u4f8b
     */
    @Bean
    public BearerSubjectAuthenticator bearerSubjectAuthenticator() {
        return new BearerSubjectAuthenticator();
    }

    /**
     * \u6ce8\u518c Web \u5f02\u5e38\u7ffb\u8bd1\u5668\u3002
     * @return \u5f02\u5e38\u7ffb\u8bd1\u5668\u5b9e\u4f8b
     */
    @Bean
    public WebExceptionTranslator webExceptionTranslator() {
        return new DefaultWebExceptionTranslator();
    }

    /**
     * \u6ce8\u518c\u57fa\u4e8e\u8def\u5f84\u7684\u8bbf\u95ee\u7b56\u7565\uff08\u9ed8\u8ba4\u653e\u884c\u5065\u5eb7\u68c0\u67e5\u4e0e\u9759\u6001\u8d44\u6e90\u8def\u5f84\uff09\u3002
     * @param environment Spring \u73af\u5883\uff08\u8bfb\u53d6 {@code ddd4j.web.public-paths}\uff09
     * @return \u8bbf\u95ee\u7b56\u7565\u5b9e\u4f8b
     */
    @Bean
    public WebAccessPolicy webAccessPolicy(Environment environment) {
        String[] publicPaths = environment.getProperty("ddd4j.web.public-paths", String[].class,
                new String[]{"/health", "/health/readiness", "/health/liveness", ReadinessEndpoint.PATH,
                        "/assets/**", "/webjars/**"});
        return new PathWebAccessPolicy(List.of(publicPaths), AuthenticationMode.REQUIRED);
    }

    /**
     * \u6ce8\u518c\u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382\uff08\u751f\u6210\u8bf7\u6c42 ID\u3001\u89e3\u6790\u5ba2\u6237\u7aef IP\uff09\u3002
     * @param environment Spring \u73af\u5883\uff08\u8bfb\u53d6 {@code ddd4j.web.trust-forwarded-headers}\uff09
     * @return \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
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
     * \u6ce8\u518c\u8bf7\u6c42\u751f\u547d\u5468\u671f\u7ba1\u7406\u5668\uff08\u8ba4\u8bc1\u4e0e\u8bbf\u95ee\u7b56\u7565\u7f16\u6392\uff09\u3002
     * @param authenticator Bearer Token \u4e3b\u4f53\u8ba4\u8bc1\u5668
     * @param accessPolicy \u8def\u5f84\u8bbf\u95ee\u7b56\u7565
     * @return \u8bf7\u6c42\u751f\u547d\u5468\u671f\u7ba1\u7406\u5668
     */
    @Bean
    public WebRequestLifecycle webRequestLifecycle(BearerSubjectAuthenticator authenticator,
                                                   WebAccessPolicy accessPolicy) {
        return new WebRequestLifecycle(authenticator, accessPolicy);
    }

    /**
     * \u6ce8\u518c\u5e42\u7b49\u751f\u547d\u5468\u671f\u7ba1\u7406\u5668\uff08\u57fa\u4e8e\u7f13\u5b58\u79df\u7ea6\uff09\u3002
     * @return \u5e42\u7b49\u751f\u547d\u5468\u671f\u7ba1\u7406\u5668
     */
    @Bean
    public WebIdempotencyLifecycle webIdempotencyLifecycle() {
        return new WebIdempotencyLifecycle(new CacheIdempotencyGuard());
    }

    /**
     * \u6ce8\u518c ddd4j WebFlux \u6838\u5fc3\u8fc7\u6ee4\u5668\uff08\u4e32\u8054\u4e0a\u4e0b\u6587\u3001\u751f\u547d\u5468\u671f\u4e0e\u5e42\u7b49\uff09\u3002
     * @param contextFactory \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
     * @param requestLifecycle \u8bf7\u6c42\u751f\u547d\u5468\u671f\u7ba1\u7406\u5668
     * @param idempotencyLifecycle \u5e42\u7b49\u751f\u547d\u5468\u671f\u7ba1\u7406\u5668
     * @return \u6838\u5fc3\u8fc7\u6ee4\u5668\u5b9e\u4f8b
     */
    @Bean
    public Ddd4jWebFluxFilter ddd4jWebFluxFilter(WebRequestContextFactory contextFactory,
                                                 WebRequestLifecycle requestLifecycle,
                                                 WebIdempotencyLifecycle idempotencyLifecycle) {
        return new Ddd4jWebFluxFilter(contextFactory, requestLifecycle, idempotencyLifecycle);
    }

    /**
     * \u6ce8\u518c\u4e0d\u4f9d\u8d56 Spring Boot Actuator \u7684\u663e\u5f0f readiness \u7aef\u70b9\u3002
     *
     * @param readinessRegistry \u8fd0\u884c\u65f6\u5c31\u7eea\u72b6\u6001\u6ce8\u518c\u8868
     * @return readiness \u63a7\u5236\u5668
     */
    @Bean
    public Ddd4jWebFluxReadinessController ddd4jWebFluxReadinessController(
            RuntimeReadinessRegistry readinessRegistry) {
        return new Ddd4jWebFluxReadinessController(readinessRegistry);
    }

    /**
     * \u6ce8\u518c\u9ed8\u8ba4\u9759\u6001\u8d44\u6e90\u6620\u5c04\u914d\u7f6e\u5668\u3002
     * @param localResourceProperteis \u672c\u5730\u9759\u6001\u8d44\u6e90\u6620\u5c04\u914d\u7f6e
     * @return \u8d44\u6e90\u6620\u5c04\u914d\u7f6e\u5668
     */
    @Bean
    public DefaultWebFluxConfigurer defaultWebFluxConfigurer(LocalResourceProperteis localResourceProperteis) {
        return new DefaultWebFluxConfigurer(localResourceProperteis);
    }

}
