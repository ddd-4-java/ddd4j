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

@Configuration(proxyBeanMethods = false)
@EnableWebMvc
public class DefaultWebMvcConfiguration {

    @Bean
    public ProfileManager profileManager(Environment environment) {
        return new ProfileManager(environment::getActiveProfiles);
    }

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
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
        localeChangeInterceptor.setParamName(Constants.LANG_PARAM_NAME);
        return localeChangeInterceptor;
    }

    @Bean
    public LocaleResolver localeResolver() {

        XHeaderLocaleResolver xheaderLocaleResolver = new XHeaderLocaleResolver();
        xheaderLocaleResolver.setDefaultLocale(Locale.getDefault());
        xheaderLocaleResolver.setDefaultTimeZone(TimeZone.getDefault());
        return xheaderLocaleResolver;
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Primary
    public LocalValidatorFactoryBean mvcValidator(NestedMessageSource messageSource) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        return factoryBean;
    }

    @Bean
    public MdcInterceptor mdcInterceptor() {
        return new MdcInterceptor();
    }

    @Bean
    public DefaultWebMvcConfigurer defaultWebMvcConfigurer(LocalResourceProperteis localResourceProperteis,
                                                           LocaleChangeInterceptor localeChangeInterceptor,
                                                           MdcInterceptor mdcInterceptor) {
        return new DefaultWebMvcConfigurer(localResourceProperteis, localeChangeInterceptor, mdcInterceptor);
    }

}
