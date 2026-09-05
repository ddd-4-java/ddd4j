/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.quarkus.cqrs.config;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.quarkus.cqrs.cache.GoodsCacheService;
import io.ddd4j.sample.quarkus.cqrs.cache.OrderCacheService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * ddd4j Quarkus 示例启动配置 Bean。
 *
 * <p>虽然 ddd4j-runtime-quarkus 已经提供了
 * {@link io.ddd4j.quarkus.context.DddContextInitializer} 自动注入核心 SPI，
 * 本配置类演示：
 * <ol>
 *   <li>如何在 Quarkus 启动期拿到 SPI Bean 引用（用于运行时校验与日志）</li>
 *   <li>如何利用 {@link io.quarkus.runtime.StartupEvent} 监听应用启动完成事件</li>
 *   <li>如何利用 {@link ApplicationScoped} Bean 暴露给其他业务 Bean 注入</li>
 * </ol>
 *
 * <h3>运行效果</h3>
 * <p>应用启动后日志中会输出类似：
 * <pre>
 * [Ddd4jSampleConfig] DomainEventPublisher = NoOpDomainEventPublisher
 * [Ddd4jSampleConfig] SubjectProvider        = AnonymousSubjectProvider
 * [Ddd4jSampleConfig] I18nProvider           = DefaultI18nProvider
 * </pre>
 *
 * <h3>扩展建议</h3>
 * <p>真实项目可在本类中加入：
 * <ul>
 *   <li>Quarkus 配置项绑定（{@code @ConfigProperty}）</li>
 *   <li>Redis / Kafka 客户端初始化</li>
 *   <li>多租户上下文预热</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationScoped
public class Ddd4jSampleConfig {

    /**
     * 核心 SPI Bean 引用（启动期注入）
     */
    @Inject
    Instance<DomainEventPublisher> domainEventPublisher;

    @Inject
    Instance<SubjectProvider> subjectProvider;

    @Inject
    Instance<I18nProvider> i18nProvider;

    /**
     * Quarkus 启动完成后回调：校验核心 SPI 是否成功注入，并打印日志。
     *
     * <p>{@code StartupEvent} 是 Quarkus 的标准启动信号，
     * ddd4j-runtime-quarkus 的 {@code DddContextInitializer} 也订阅同一事件，
     * 因此本方法与它的执行顺序不固定，业务方应避免依赖顺序。
     *
     * @param event 启动事件
     */
    void onStart(@Observes StartupEvent event) {
        log.info("[Ddd4jSampleConfig] Quarkus started, verifying ddd4j SPI services...");

        logSPI("DomainEventPublisher", domainEventPublisher);
        logSPI("SubjectProvider", subjectProvider);
        logSPI("I18nProvider", i18nProvider);

        // 初始化业务缓存域（Caffeine 本地缓存后端）
        initCaches();

        log.info("[Ddd4jSampleConfig] Sample application is ready at http://localhost:8080");
        log.info("[Ddd4jSampleConfig] Try: curl http://localhost:8080/orders/health");
    }

    /**
     * 初始化 Order/Goods 的 CacheKit 缓存域（CQRS 读侧缓存增强）。
     */
    private void initCaches() {
        // 订单统计缓存
        CacheKit.build(OrderCacheService.BIZ_ORDER_STATS,
                builder -> builder
                        .maximumSize(10000)
                        .expireAfterWriteSeconds(300)
                        .initialCapacity(128)
                        .recordStats(true));
        // 买家订单计数缓存
        CacheKit.build(OrderCacheService.BIZ_BUYER_ORDER_COUNT,
                builder -> builder
                        .maximumSize(10000)
                        .expireAfterWriteSeconds(3600)
                        .initialCapacity(256)
                        .recordStats(true));
        // 商品详情缓存
        CacheKit.build(GoodsCacheService.BIZ_GOODS_DETAIL,
                builder -> builder
                        .maximumSize(20000)
                        .expireAfterWriteSeconds(600)
                        .initialCapacity(256)
                        .recordStats(true));
        // 商品列表缓存
        CacheKit.build(GoodsCacheService.BIZ_GOODS_LIST,
                builder -> builder
                        .maximumSize(2000)
                        .expireAfterWriteSeconds(120)
                        .initialCapacity(128)
                        .recordStats(true));
        log.info("[Ddd4jSampleConfig] 已注册 {} 个缓存域: {}",
                CacheKit.getCacheNames().size(), CacheKit.getCacheNames());
    }

    /**
     * 供其他业务 Bean 注入使用的便捷方法。
     *
     * @return 进程内领域事件发布者
     */
    public Optional<DomainEventPublisher> domainEventPublisher() {
        return domainEventPublisher.isUnsatisfied() ? Optional.empty() : Optional.of(domainEventPublisher.get());
    }

    private <T> void logSPI(String name, Instance<T> instance) {
        if (instance.isUnsatisfied()) {
            log.warn("[Ddd4jSampleConfig] {} NOT FOUND (unsatisfied)", name);
            return;
        }
        if (instance.isAmbiguous()) {
            log.warn("[Ddd4jSampleConfig] {} AMBIGUOUS (multiple beans)", name);
            return;
        }
        T bean = instance.get();
        log.info("[Ddd4jSampleConfig] {} = {}", name, bean.getClass().getSimpleName());
    }
}
