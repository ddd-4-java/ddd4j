package io.ddd4j.sample.spring;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.reactive.config.BlockingExecutionConfigurer;
import org.springframework.web.reactive.config.DelegatingWebFluxConfiguration;
import org.springframework.web.reactive.handler.WebFluxResponseStatusExceptionHandler;
import org.springframework.web.server.i18n.LocaleContextResolver;
import reactor.core.scheduler.Schedulers;

/**
 * 显式原生 WebFlux 模式，保留外部 ddd4j 提供的 localeContextResolver 名称。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class SampleReactiveConfiguration extends DelegatingWebFluxConfiguration {
    /**
     * \u4e3a\u963b\u585e\u5f0f\u5546\u54c1\u4ea4\u6613\u5904\u7406\u914d\u7f6e Spring WebFlux \u5de5\u4f5c\u6267\u884c\u7ec4\u4ef6\u3002
     * @param configurer \u8bbe\u7f6e WebFlux \u963b\u585e\u5904\u7406\u4f7f\u7528\u7684\u914d\u7f6e\u5165\u53e3
     */
    @Override
    protected void configureBlockingExecution(BlockingExecutionConfigurer configurer) {
        super.configureBlockingExecution(configurer);
        configurer.setExecutor(new TaskExecutorAdapter(Schedulers.boundedElastic()::schedule))
                .setControllerMethodPredicate(method -> CatalogTradeController.class.isAssignableFrom(method.getBeanType()));
    }

    /**
     * \u4fdd\u7559 WebFlux \u4e3a\u672a\u5339\u914d\u5730\u5740\u751f\u6210\u7684\u539f\u751f HTTP \u72b6\u6001\u3002
     *
     * @return \u6ce8\u518c\u5230 WebFlux \u5f02\u5e38\u5904\u7406\u94fe\u7684\u72b6\u6001\u5904\u7406\u5668
     */
    @Bean
    @Order(-3)
    @Profile("catalog-trade")
    public WebFluxResponseStatusExceptionHandler catalogHttpStatusHandler() {
        return new WebFluxResponseStatusExceptionHandler();
    }

    /**
     * \u6ce8\u518c\u5904\u7406\u5e42\u7b49\u8bf7\u6c42\u7684\u672c\u5730\u7f13\u5b58\uff0c\u5e76\u5728\u5e94\u7528\u5173\u95ed\u65f6\u6ce8\u9500\u672c Bean \u521b\u5efa\u7684\u5b9e\u4f8b\u3002
     * @return \u6309\u6240\u6709\u6743\u6ce8\u9500\u7f13\u5b58\u7684\u5173\u95ed\u56de\u8c03
     */
    @Bean(destroyMethod = "close")
    @Profile("catalog-trade")
    public AutoCloseable catalogIdempotencyCache() {
        String name = "ddd4j-web-idempotency";
        Cache<String, Object> cache = CacheKit.buildLocalIfAbsent(name, 300L);
        return () -> CacheKit.unregisterLocalIfOwned(name, cache);
    }

    /**
     * @return 原生配置默认解析器；实际 WebHttpHandler 使用外部配置的 localeContextResolver
     */
    @Override
    @Bean("nativeWebFluxLocaleContextResolver")
    public LocaleContextResolver localeContextResolver() {
        return super.localeContextResolver();
    }
}
