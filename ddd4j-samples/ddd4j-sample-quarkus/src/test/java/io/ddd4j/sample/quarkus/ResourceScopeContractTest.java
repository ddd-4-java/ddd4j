package io.ddd4j.sample.quarkus;

import io.ddd4j.sample.quarkus.goods.web.GoodsQueryResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quarkus REST 资源在三条兼容线保持相同的 CDI 生命周期。
 */
class ResourceScopeContractTest {

    /**
     * 验证<code>GoodsQueryResource.class.isAnnotationPresent(ApplicationScoped.class)</code>为真，<code>CatalogTradeResource.class.isAnnotationPresent(Singleton.class)</code>为真。
     */
    @Test
    void resourcesAreApplicationScoped() {
        assertTrue(GoodsQueryResource.class.isAnnotationPresent(ApplicationScoped.class));
        assertTrue(CatalogTradeResource.class.isAnnotationPresent(Singleton.class));
    }
}
