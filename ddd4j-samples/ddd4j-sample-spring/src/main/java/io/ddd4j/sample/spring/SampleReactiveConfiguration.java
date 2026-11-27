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
     * 为阻塞式商品交易处理配置 Spring WebFlux 工作执行组件。
     *
     * @param configurer 设置 WebFlux 阻塞处理使用的配置入口
     */
    @Override
    protected void configureBlockingExecution(BlockingExecutionConfigurer configurer) {
        super.configureBlockingExecution(configurer);
        configurer.setExecutor(new TaskExecutorAdapter(Schedulers.boundedElastic()::schedule))
                .setControllerMethodPredicate(method -> CatalogTradeController.class.isAssignableFrom(method.getBeanType()));
    }

    /**
     * 保留 WebFlux 为未匹配地址生成的原生 HTTP 状态。
     *
     * @return 注册到 WebFlux 异常处理链的状态处理器
     */
    @Bean
    @Order(-3)
    @Profile("catalog-trade")
    public WebFluxResponseStatusExceptionHandler catalogHttpStatusHandler() {
        return new WebFluxResponseStatusExceptionHandler();
    }

    /**
     * 注册处理幂等请求的本地缓存，并在应用关闭时注销本 Bean 创建的实例。
     *
     * @return 按所有权注销缓存的关闭回调
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
