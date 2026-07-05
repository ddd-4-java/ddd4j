package io.ddd4j.sample.spring.cqrs.config;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.sample.spring.cqrs.cache.GoodsCacheService;
import io.ddd4j.sample.spring.cqrs.cache.OrderCacheService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 示例配置。
 *
 * <p>集中管理 ddd4j 模块的 Spring 装配：
 * <ul>
 *   <li>初始化 {@link CacheKit} 缓存域（Caffeine 本地缓存后端）</li>
 *   <li>ddd4j-runtime-spring 自动完成 SPI 注入（DomainEventPublisher → SpringDomainEventPublisher）</li>
 *   <li>ddd4j-mq-spring 自动完成 @MQEventListener 扫描与 MQEventPublisher 注入</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SpringSampleConfig {

    /**
     * 启动期初始化业务缓存。
     *
     * <p>使用 {@link CacheKit#build(String, java.util.function.Function)} 注册缓存域。
     * 所有缓存统一使用 Caffeine 本地缓存引擎（默认），
     * 切换为 Redis/Redisson 只需替换 {@code CacheKit.build} 为对应的分布式缓存实现。
     */
    @PostConstruct
    public void initCaches() {
        log.info("========== 开始初始化 ddd4j 缓存（默认后端：Caffeine） ==========");

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

        // 商品详情缓存（CQRS 读侧优先读取）
        CacheKit.build(GoodsCacheService.BIZ_GOODS_DETAIL,
                builder -> builder
                        .maximumSize(20000)
                        .expireAfterWriteSeconds(600)
                        .initialCapacity(256)
                        .recordStats(true));

        // 商品列表缓存（CQRS 读侧缓存列表）
        CacheKit.build(GoodsCacheService.BIZ_GOODS_LIST,
                builder -> builder
                        .maximumSize(2000)
                        .expireAfterWriteSeconds(120)
                        .initialCapacity(128)
                        .recordStats(true));

        log.info("========== ddd4j 缓存初始化完成：已注册 {} 个业务域 ==========",
                CacheKit.getCacheNames().size());
        log.info("已注册的业务缓存: {}", CacheKit.getCacheNames());
    }
}
