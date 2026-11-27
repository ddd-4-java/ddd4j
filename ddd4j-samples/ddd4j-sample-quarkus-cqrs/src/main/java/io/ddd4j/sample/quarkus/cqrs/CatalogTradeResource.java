package io.ddd4j.sample.quarkus.cqrs;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cqrs.command.CommandBus;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.CatalogCqrsResource;
import javax.inject.Inject;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Path;

/** Quarkus \u5546\u54c1\u4ea4\u6613 HTTP \u5165\u53e3\uff0c\u63a5\u5165\u793a\u4f8b\u7684\u547d\u4ee4\u603b\u7ebf\u53ca\u8ba2\u5355\u6295\u5f71\u3002 */
@io.quarkus.runtime.Startup
@ApplicationScoped
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogCqrsResource {
    /** \u8ba2\u5355\u5e42\u7b49\u62e6\u622a\u5668\u67e5\u8be2\u7684\u672c\u5730\u7f13\u5b58\u540d\u79f0\u3002 */
    private static final String CACHE_NAME = "ddd4j-web-idempotency";
    /** \u5f53\u524d\u8d44\u6e90\u521b\u5efa\u4e14\u8d1f\u8d23\u6ce8\u9500\u7684\u5e42\u7b49\u8bf7\u6c42\u7f13\u5b58\u3002 */
    private Cache<String, Object> ownedCache;

    /**
     * 执行 <code>configure</code>，调用 <code>executor.api</code>。
     *
     * @param executor 执行延迟投影任务的线程池
     * @param commands 传入此操作的 <code>CommandBus</code> 类型 <code>commands</code>
     */
    @Inject
    void configure(CatalogCommandExecutor executor, CommandBus commands) {
        initializeProjection(new CatalogCqrs(executor.api(), commands));
    }

    /** \u6ce8\u518c\u5904\u7406\u5e42\u7b49\u8bf7\u6c42\u7684\u672c\u5730\u7f13\u5b58\uff0c\u4fdd\u7559\u672c\u8d44\u6e90\u521b\u5efa\u7684\u7f13\u5b58\u5f15\u7528\u3002 */
    @PostConstruct
    void registerIdempotencyCache() {
        ownedCache = CacheKit.buildLocalIfAbsent(CACHE_NAME, 300L);
    }

    /** \u4ec5\u6ce8\u9500\u7531\u672c\u8d44\u6e90\u6ce8\u518c\u7684\u5e42\u7b49\u8bf7\u6c42\u7f13\u5b58\u3002 */
    @PreDestroy
    void closeIdempotencyCache() {
        CacheKit.unregisterLocalIfOwned(CACHE_NAME, ownedCache);
    }
}
