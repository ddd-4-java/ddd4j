/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.cmpt.webflux;

import io.ddd4j.boot.cmpt.webflux.config.LocalResourceProperteis;
import io.ddd4j.boot.cmpt.webflux.error.GlobalExceptionHandler;
import io.ddd4j.boot.core.ProfileManager;
import org.springframework.biz.web.server.ReactiveRequestContextFilter;
import org.springframework.biz.web.server.i18n.XHeaderLocaleContextResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.i18n.LocaleContextResolver;

import java.util.Locale;
import java.util.TimeZone;

@Configuration(proxyBeanMethods = false)
@EnableWebFlux
@EnableConfigurationProperties(LocalResourceProperteis.class)
public class DefaultWebFluxConfiguration {

    @Bean
    public ProfileManager profileManager(Environment environment) {
        return new ProfileManager(environment);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
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

	/*
	@Bean
	@Order(-2)
	public ErrorWebExceptionHandler errorWebExceptionHandler(ErrorAttributes errorAttributes,
			ResourceProperties resourceProperties, ObjectProvider<ViewResolver> viewResolvers,
			ServerCodecConfigurer serverCodecConfigurer, ApplicationContext applicationContext) {
		GlobalErrorWebExceptionHandler exceptionHandler = new GlobalErrorWebExceptionHandler(errorAttributes,
				resourceProperties,  applicationContext);
		exceptionHandler.setViewResolvers(viewResolvers.orderedStream().collect(Collectors.toList()));
		exceptionHandler.setMessageWriters(serverCodecConfigurer.getWriters());
		exceptionHandler.setMessageReaders(serverCodecConfigurer.getReaders());
		return exceptionHandler;
	}*/

    @Bean
    public DefaultWebFluxConfigurer defaultWebFluxConfigurer(LocalResourceProperteis localResourceProperteis) {
        return new DefaultWebFluxConfigurer(localResourceProperteis);
    }

}
