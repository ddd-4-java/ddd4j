package io.ddd4j.sample.spring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.DelegatingWebFluxConfiguration;
import org.springframework.web.server.i18n.LocaleContextResolver;

/** 显式原生 WebFlux 模式，保留外部 ddd4j 提供的 localeContextResolver 名称。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class SampleReactiveConfiguration extends DelegatingWebFluxConfiguration {
    /**
     * @return 原生配置默认解析器；实际 WebHttpHandler 使用外部配置的 localeContextResolver
     */
    @Override
    @Bean("nativeWebFluxLocaleContextResolver")
    public LocaleContextResolver localeContextResolver() {
        return super.localeContextResolver();
    }
}
