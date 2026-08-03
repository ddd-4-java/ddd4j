/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webmvc;

import io.ddd4j.core.ProfileManager;
import io.ddd4j.core.constant.Constants;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.CacheIdempotencyGuard;
import io.ddd4j.web.core.ClientIpResolver;
import io.ddd4j.web.core.PathWebAccessPolicy;
import io.ddd4j.web.core.RequestIdGenerator;
import io.ddd4j.web.core.AuthenticationMode;
import io.ddd4j.web.core.WebAccessPolicy;
import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.WebExceptionTranslator;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestLifecycle;
import io.ddd4j.web.webmvc.config.LocalResourceProperteis;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.biz.context.NestedMessageSource;
import org.springframework.biz.web.servlet.i18n.XHeaderLocaleResolver;
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
     * Profile 管理器：根据活跃环境配置切换。
     */
    @Bean
    public ProfileManager profileManager(Environment environment) {
        return new ProfileManager(environment::getActiveProfiles);
    }

    /**
     * 请求上下文过滤器（线程上下文可继承）。
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
     * 语言切换拦截器。
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
        localeChangeInterceptor.setParamName(Constants.LANG_PARAM_NAME);
        return localeChangeInterceptor;
    }

    /**
     * 区域解析器（基于请求头 X-Locale）。
     */
    @Bean
    public LocaleResolver localeResolver() {

        XHeaderLocaleResolver xheaderLocaleResolver = new XHeaderLocaleResolver();
        xheaderLocaleResolver.setDefaultLocale(Locale.getDefault());
        xheaderLocaleResolver.setDefaultTimeZone(TimeZone.getDefault());
        return xheaderLocaleResolver;
    }

    /**
     * MVC 验证器（集成国际化消息源）。
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Primary
    public LocalValidatorFactoryBean mvcValidator(NestedMessageSource messageSource) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        return factoryBean;
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
    public Ddd4jWebMvcExceptionHandler ddd4jWebMvcExceptionHandler(WebExceptionTranslator exceptionTranslator) {
        return new Ddd4jWebMvcExceptionHandler(exceptionTranslator);
    }

    @Bean
    public WebAccessPolicy webAccessPolicy(Environment environment) {
        String[] publicPaths = environment.getProperty("ddd4j.web.public-paths", String[].class,
                new String[]{"/health", "/health/readiness", "/health/liveness", "/assets/**", "/webjars/**"});
        return new PathWebAccessPolicy(List.of(publicPaths), AuthenticationMode.REQUIRED);
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
    public Ddd4jWebMvcInterceptor ddd4jWebMvcInterceptor(WebRequestContextFactory contextFactory,
                                                         WebRequestLifecycle requestLifecycle,
                                                         WebIdempotencyLifecycle idempotencyLifecycle) {
        return new Ddd4jWebMvcInterceptor(contextFactory, requestLifecycle, idempotencyLifecycle);
    }

    /**
     * 自定义 WebMVC 配置器。
     */
    @Bean
    public DefaultWebMvcConfigurer defaultWebMvcConfigurer(LocalResourceProperteis localResourceProperteis,
                                                           LocaleChangeInterceptor localeChangeInterceptor,
                                                           Ddd4jWebMvcInterceptor ddd4jWebMvcInterceptor) {
        return new DefaultWebMvcConfigurer(localResourceProperteis, localeChangeInterceptor, ddd4jWebMvcInterceptor);
    }

}
