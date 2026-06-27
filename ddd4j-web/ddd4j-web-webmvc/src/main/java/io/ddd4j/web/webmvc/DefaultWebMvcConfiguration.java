/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.web.webmvc;

import io.ddd4j.web.config.LocalResourceProperteis;
import io.ddd4j.core.Constants;
import io.ddd4j.core.ProfileManager;
import io.ddd4j.spring.web.Slf4jMDCInterceptor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.biz.context.NestedMessageSource;
import org.springframework.biz.web.servlet.i18n.XHeaderLocaleResolver;
import org.springframework.biz.web.servlet.theme.NestedThemeResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import org.springframework.ui.context.support.ResourceBundleThemeSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ThemeResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.theme.CookieThemeResolver;
import org.springframework.web.servlet.theme.SessionThemeResolver;
import org.springframework.web.servlet.theme.ThemeChangeInterceptor;

import java.util.LinkedList;
import java.util.List;
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

    /*###########Spring MVC 主题支持########### */
    /*参考 ： http://blog.csdn.net/wutbiao/article/details/7450281 */

    @Bean
    public ThemeChangeInterceptor themeChangeInterceptor() {
        ThemeChangeInterceptor themeChangeInterceptor = new ThemeChangeInterceptor();
        themeChangeInterceptor.setParamName(Constants.THEME_PARAM_NAME);
        return themeChangeInterceptor;
    }

    @Bean
    public ResourceBundleThemeSource themeSource() {
        ResourceBundleThemeSource themeSource = new ResourceBundleThemeSource();
        themeSource.setBasenamePrefix(Constants.THEME_SOURCE_CLASSPATH);
        return themeSource;
    }

    @Bean
    public ThemeResolver themeResolver() {

        NestedThemeResolver nestedThemeResolver = new NestedThemeResolver();
        nestedThemeResolver.setDefaultThemeName(Constants.THEME_PARAM_DEFAULT);

        List<ThemeResolver> resolvers = new LinkedList<ThemeResolver>();

        //基于Session的主题解析
        SessionThemeResolver sessionThemeResolver = new SessionThemeResolver();
        sessionThemeResolver.setDefaultThemeName(Constants.THEME_PARAM_DEFAULT);
        resolvers.add(sessionThemeResolver);

        //基于Cokie的主题解析
        CookieThemeResolver cookieThemeResolver = new CookieThemeResolver();
        cookieThemeResolver.setCookieName(Constants.THEME_PARAM_NAME);
        cookieThemeResolver.setDefaultThemeName(Constants.THEME_PARAM_DEFAULT);
        resolvers.add(cookieThemeResolver);

        return nestedThemeResolver;
    }

    @Bean
    public Slf4jMDCInterceptor slf4jMDCInterceptor() {
        return new Slf4jMDCInterceptor();
    }

    @Bean
    public DefaultWebMvcConfigurer defaultWebMvcConfigurer(LocalResourceProperteis localResourceProperteis,
                                                           ThemeChangeInterceptor themeChangeInterceptor, LocaleChangeInterceptor localeChangeInterceptor,
                                                           Slf4jMDCInterceptor slf4jMDCInterceptor) {
        return new DefaultWebMvcConfigurer(localResourceProperteis, themeChangeInterceptor, localeChangeInterceptor,
                slf4jMDCInterceptor);
    }

}
