/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux;

import io.ddd4j.web.webflux.config.LocalResourceProperteis;
import io.ddd4j.web.webflux.error.GlobalExceptionHandler;
import io.ddd4j.core.ProfileManager;
import org.springframework.biz.web.server.ReactiveRequestContextFilter;
import org.springframework.biz.web.server.i18n.XHeaderLocaleContextResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.i18n.LocaleContextResolver;

import java.util.Locale;
import java.util.TimeZone;

@Configuration(proxyBeanMethods = false)
@EnableWebFlux
public class DefaultWebFluxConfiguration {

    @Bean
    public ProfileManager profileManager(Environment environment) {
        return new ProfileManager(environment::getActiveProfiles);
    }

    @Bean
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
    public GlobalExceptionHandler defaultExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public DefaultWebFluxConfigurer defaultWebFluxConfigurer(LocalResourceProperteis localResourceProperteis) {
        return new DefaultWebFluxConfigurer(localResourceProperteis);
    }

}
