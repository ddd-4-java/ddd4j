package io.ddd4j.sample.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import io.ddd4j.sample.quarkus.goods.web.GoodsQueryResource;
import io.ddd4j.sample.quarkus.order.web.OrderResource;
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
                OrderResource.class}) {
            assertTrue(resource.isAnnotationPresent(ApplicationScoped.class), resource.getName());
        }
    }
}
