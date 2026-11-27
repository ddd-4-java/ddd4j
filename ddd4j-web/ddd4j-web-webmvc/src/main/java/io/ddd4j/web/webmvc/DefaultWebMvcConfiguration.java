/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webmvc;

import io.ddd4j.core.ProfileManager;
import io.ddd4j.core.constant.Constants;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.RequestIdGenerator;
import io.ddd4j.web.core.health.ReadinessEndpoint;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.webmvc.config.LocalResourceProperteis;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.extension.web.servlet.i18n.XHeaderLocaleResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;
import java.util.List;
import java.util.TimeZone;

/**
 * Spring WebMVC 默认配置。
 * <p>装配 Profile 管理器、请求上下文过滤器、国际化解析器、验证器及自定义配置器。</p>
 */
@Configuration(proxyBeanMethods = false)
public class DefaultWebMvcConfiguration {

    /**
     * Profile \u7ba1\u7406\u5668\uff1a\u6839\u636e\u6d3b\u8dc3\u73af\u5883\u914d\u7f6e\u5207\u6362\u3002
     *
     * @param environment Spring \u73af\u5883
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public ProfileManager profileManager(Environment environment) {
        return new ProfileManager(environment::getActiveProfiles);
    }

    /**
     * \u8bf7\u6c42\u4e0a\u4e0b\u6587\u8fc7\u6ee4\u5668\uff08\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u53ef\u7ee7\u627f\uff09\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean(name = "ddd4jRequestContextFilter")
    public RequestContextFilter requestContextFilter() {
        RequestContextFilter contextFilter = new RequestContextFilter();
        contextFilter.setThreadContextInheritable(true);
        return contextFilter;
    }

    /*########### SpringMVC本地化支持 ###########*/

    /* 参考 ：
			http://blog.csdn.net/wutbiao/article/details/7454345
			http://yvonxiao.iteye.com/blog/1005183
		*/

    /**
     * \u8bed\u8a00\u5207\u6362\u62e6\u622a\u5668\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
        localeChangeInterceptor.setParamName(Constants.LANG_PARAM_NAME);
        return localeChangeInterceptor;
    }

    /**
     * \u533a\u57df\u89e3\u6790\u5668\uff08\u57fa\u4e8e\u8bf7\u6c42\u5934 X-Locale\uff09\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public LocaleResolver localeResolver() {

        XHeaderLocaleResolver xheaderLocaleResolver = new XHeaderLocaleResolver();
        xheaderLocaleResolver.setDefaultLocale(Locale.getDefault());
        xheaderLocaleResolver.setDefaultTimeZone(TimeZone.getDefault());
        return xheaderLocaleResolver;
    }

    /**
     * MVC \u9a8c\u8bc1\u5668\uff08\u96c6\u6210\u56fd\u9645\u5316\u6d88\u606f\u6e90\uff09\u3002
     *
     * @param messageSource \u6d88\u606f\u6e90
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Primary
    public LocalValidatorFactoryBean mvcValidator(NestedMessageSource messageSource) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        return factoryBean;
    }

    /**
     * \u521b\u5efa\u6216\u914d\u7f6e bearerSubjectAuthenticator \u7ec4\u4ef6\u3002
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ed3\u679c
     */
    @Bean
    public BearerSubjectAuthenticator bearerSubjectAuthenticator() {
        return new BearerSubjectAuthenticator();
    }

    /**
     * \u521b\u5efa\u6216\u914d\u7f6e webExceptionTranslator \u7ec4\u4ef6\u3002
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ed3\u679c
     */
    @Bean
    public WebExceptionTranslator webExceptionTranslator() {
        return new DefaultWebExceptionTranslator();
    }

    /**
     * \u521b\u5efa\u6216\u914d\u7f6e ddd4jWebMvcExceptionHandler \u7ec4\u4ef6\u3002
     * @param exceptionTranslator \u5f02\u5e38\u8f6c\u6362\u5668
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ed3\u679c
     */
    @Bean
    public Ddd4jWebMvcExceptionHandler ddd4jWebMvcExceptionHandler(WebExceptionTranslator exceptionTranslator) {
        return new Ddd4jWebMvcExceptionHandler(exceptionTranslator);
    }

    /**
     * \u521b\u5efa\u6216\u914d\u7f6e webAccessPolicy \u7ec4\u4ef6\u3002
     * @param environment Spring \u73af\u5883
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ed3\u679c
     */
    @Bean
    public WebAccessPolicy webAccessPolicy(Environment environment) {
        String[] publicPaths = environment.getProperty("ddd4j.web.public-paths", String[].class,
                new String[]{"/health", "/health/readiness", "/health/liveness", ReadinessEndpoint.PATH,
                        "/assets/**", "/webjars/**"});
        return new PathWebAccessPolicy(List.of(publicPaths), AuthenticationMode.REQUIRED);
    }

    /**
     * \u521b\u5efa\u6216\u914d\u7f6e webRequestContextFactory \u7ec4\u4ef6\u3002
     * @param environment Spring \u73af\u5883
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ed3\u679c
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
     * \u521b\u5efa\u6216\u914d\u7f6e webRequestLifecycle \u7ec4\u4ef6\u3002
     * @param authenticator Bearer \u8ba4\u8bc1\u5668
     * @param accessPolicy \u65b9\u6cd5\u53c2\u6570
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ed3\u679c
     */
    @Bean
    public WebRequestLifecycle webRequestLifecycle(BearerSubjectAuthenticator authenticator,
                                                   WebAccessPolicy accessPolicy) {
        return new WebRequestLifecycle(authenticator, accessPolicy);
    }

    /**
     * \u521b\u5efa\u6216\u914d\u7f6e webIdempotencyLifecycle \u7ec4\u4ef6\u3002
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ed3\u679c
     */
    @Bean
    public WebIdempotencyLifecycle webIdempotencyLifecycle() {
        return new WebIdempotencyLifecycle(new CacheIdempotencyGuard());
    }

    /**
     * \u521b\u5efa\u6216\u914d\u7f6e ddd4jWebMvcInterceptor \u7ec4\u4ef6\u3002
     * @param contextFactory \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
     * @param requestLifecycle \u8bf7\u6c42\u751f\u547d\u5468\u671f
     * @param idempotencyLifecycle \u65b9\u6cd5\u53c2\u6570
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ed3\u679c
     */
    @Bean
    public Ddd4jWebMvcInterceptor ddd4jWebMvcInterceptor(WebRequestContextFactory contextFactory,
                                                         WebRequestLifecycle requestLifecycle,
                                                         WebIdempotencyLifecycle idempotencyLifecycle) {
        return new Ddd4jWebMvcInterceptor(contextFactory, requestLifecycle, idempotencyLifecycle);
    }

    /**
     * \u6ce8\u518c\u4e0d\u4f9d\u8d56 Spring Boot Actuator \u7684\u663e\u5f0f readiness \u7aef\u70b9\u3002
     *
     * @param readinessRegistry \u5c31\u7eea\u72b6\u6001\u6ce8\u518c\u8868
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public Ddd4jWebMvcReadinessController ddd4jWebMvcReadinessController(
            RuntimeReadinessRegistry readinessRegistry) {
        return new Ddd4jWebMvcReadinessController(readinessRegistry);
    }

    /**
     * \u81ea\u5b9a\u4e49 WebMVC \u914d\u7f6e\u5668\u3002
     *
     * @param localeChangeInterceptor \u8bed\u8a00\u73af\u5883\u5207\u6362\u62e6\u622a\u5668
     * @param localResourceProperteis \u672c\u5730\u8d44\u6e90\u914d\u7f6e
     * @param ddd4jWebMvcInterceptor DDD4J Web \u62e6\u622a\u5668
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public DefaultWebMvcConfigurer defaultWebMvcConfigurer(LocalResourceProperteis localResourceProperteis,
                                                           LocaleChangeInterceptor localeChangeInterceptor,
                                                           Ddd4jWebMvcInterceptor ddd4jWebMvcInterceptor) {
        return new DefaultWebMvcConfigurer(localResourceProperteis, localeChangeInterceptor, ddd4jWebMvcInterceptor);
    }

}
