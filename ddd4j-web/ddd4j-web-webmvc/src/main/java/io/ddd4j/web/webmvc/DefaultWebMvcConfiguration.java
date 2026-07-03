/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webmvc.webmvc;

import io.ddd4j.core.constant.Constants;
import io.ddd4j.core.ProfileManager;
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
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Spring WebMVC 默认配置。
 * <p>装配 Profile 管理器、请求上下文过滤器、国际化解析器、验证器及自定义配置器。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebMvc
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
    @Bean
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

    /**
     * MDC 日志拦截器。
     */
    @Bean
    public MdcInterceptor mdcInterceptor() {
        return new MdcInterceptor();
    }

    /**
     * 自定义 WebMVC 配置器。
     */
    @Bean
    public DefaultWebMvcConfigurer defaultWebMvcConfigurer(LocalResourceProperteis localResourceProperteis,
                                                           LocaleChangeInterceptor localeChangeInterceptor,
                                                           MdcInterceptor mdcInterceptor) {
        return new DefaultWebMvcConfigurer(localResourceProperteis, localeChangeInterceptor, mdcInterceptor);
    }

}
