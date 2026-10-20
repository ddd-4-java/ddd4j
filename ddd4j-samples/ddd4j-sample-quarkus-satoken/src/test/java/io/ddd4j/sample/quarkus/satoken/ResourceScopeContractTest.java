package io.ddd4j.sample.quarkus.satoken;

import jakarta.enterprise.context.ApplicationScoped;
import io.ddd4j.sample.quarkus.satoken.goods.web.GoodsQueryResource;
import io.ddd4j.sample.quarkus.satoken.goods.web.GoodsResource;
import io.ddd4j.sample.quarkus.satoken.order.web.OrderResource;
import io.ddd4j.sample.quarkus.satoken.rbac.AuthenticationResource;
import io.ddd4j.sample.quarkus.satoken.rbac.AuthorizationResource;
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
                GoodsResource.class,
                OrderResource.class,
                AuthenticationResource.class,
                AuthorizationResource.class}) {
            assertTrue(resource.isAnnotationPresent(ApplicationScoped.class), resource.getName());
        }
    }
}
