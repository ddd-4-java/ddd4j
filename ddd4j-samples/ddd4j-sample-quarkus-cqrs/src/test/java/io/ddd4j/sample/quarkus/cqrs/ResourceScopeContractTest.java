package io.ddd4j.sample.quarkus.cqrs;

import jakarta.enterprise.context.ApplicationScoped;
import io.ddd4j.sample.quarkus.cqrs.goods.web.GoodsQueryResource;
import io.ddd4j.sample.quarkus.cqrs.goods.web.GoodsReadResource;
import io.ddd4j.sample.quarkus.cqrs.order.web.OrderCQRSQueryResource;
import io.ddd4j.sample.quarkus.cqrs.order.web.OrderQueryResource;
import io.ddd4j.sample.quarkus.cqrs.order.web.OrderResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quarkus REST 资源在三条兼容线保持相同的 CDI 生命周期。
 */
class ResourceScopeContractTest {

    @Test
    void resourcesAreApplicationScoped() {
        for (Class<?> resource : new Class<?>[]{
                GoodsQueryResource.class,
                GoodsReadResource.class,
                OrderCQRSQueryResource.class,
                OrderQueryResource.class,
                OrderResource.class}) {
            assertTrue(resource.isAnnotationPresent(ApplicationScoped.class), resource.getName());
        }
    }
}
