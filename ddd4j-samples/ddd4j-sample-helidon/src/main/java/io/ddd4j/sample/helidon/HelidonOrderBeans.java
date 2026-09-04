package io.ddd4j.sample.helidon;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

/**
 * Helidon CDI 中共享 Order 端口的本地示例装配。
 */
@ApplicationScoped
public class HelidonOrderBeans {

    private static final String IDEMPOTENCY_CACHE_NAME = "ddd4j-web-idempotency";
    private static final long IDEMPOTENCY_CACHE_TTL_SECONDS = 300L;

    @PostConstruct
    void initializeIdempotencyCache() {
        CacheKit.build(IDEMPOTENCY_CACHE_NAME, IDEMPOTENCY_CACHE_TTL_SECONDS);
    }

    @PreDestroy
    void destroyIdempotencyCache() {
        CacheKit.unregister(IDEMPOTENCY_CACHE_NAME);
    }

    @Produces
    @Dependent
    InMemoryOrderAdapters orderAdapters() {
        return new InMemoryOrderAdapters();
    }

    @Produces
    @ApplicationScoped
    OrderApplicationService orderApplicationService(InMemoryOrderAdapters adapters) {
        return new OrderApplicationService(adapters, adapters, adapters, adapters, adapters);
    }
}
