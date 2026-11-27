package io.ddd4j.sample.quarkus.cqrs;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.CatalogCqrsResource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

/**
 * Quarkus 商品交易 HTTP 入口，接入示例的命令总线及订单投影。
 */
@io.quarkus.runtime.Startup
@Singleton
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogCqrsResource {
    /**
     * 订单幂等拦截器查询的本地缓存名称。
     */
    private static final String CACHE_NAME = "ddd4j-web-idempotency";
    /**
     * 当前资源创建且负责注销的幂等请求缓存。
     */
    private Cache<String, Object> ownedCache;

    /**
     * 使用给定命令执行器和命令总线装配商品交易入口。
     *
     * @param executor 注册到命令总线的商品交易命令执行器
     * @param commands 示例框架装配的命令总线
     */
    @Inject
    public CatalogTradeResource(CatalogCommandExecutor executor, CommandBus commands) {
        super(new CatalogCqrs(executor.api(), commands));
    }

    /**
     * 注册处理幂等请求的本地缓存，保留本资源创建的缓存引用。
     */
    @PostConstruct
    void registerIdempotencyCache() {
        ownedCache = CacheKit.buildLocalIfAbsent(CACHE_NAME, 300L);
    }

    /**
     * 仅注销由本资源注册的幂等请求缓存。
     */
    @PreDestroy
    void closeIdempotencyCache() {
        CacheKit.unregisterLocalIfOwned(CACHE_NAME, ownedCache);
    }
}
