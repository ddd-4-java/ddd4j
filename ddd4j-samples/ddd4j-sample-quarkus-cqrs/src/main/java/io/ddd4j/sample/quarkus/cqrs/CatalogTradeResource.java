package io.ddd4j.sample.quarkus.cqrs;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.cqrs.command.CommandBus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import io.ddd4j.sample.order.adapter.http.CatalogCqrsResource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

@io.quarkus.runtime.Startup
@Singleton
@Path("/orders/catalog")
public class CatalogTradeResource extends CatalogCqrsResource {
    private static final String CACHE_NAME = "ddd4j-web-idempotency";
    private Cache<String, Object> ownedCache;

    @Inject
    public CatalogTradeResource(CatalogCommandExecutor executor, CommandBus commands) {
        super(new CatalogCqrs(executor.api(), commands));
    }

    @PostConstruct
    void registerIdempotencyCache() {
        ownedCache = CacheKit.buildLocalIfAbsent(CACHE_NAME, 300L);
    }

    @PreDestroy
    void closeIdempotencyCache() {
        CacheKit.unregisterLocalIfOwned(CACHE_NAME, ownedCache);
    }
}
