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

    /** \u9a8c\u8bc1<code>GoodsQueryResource.class.isAnnotationPresent(ApplicationScoped.class)</code>\u4e3a\u771f\uff0c<code>CatalogTradeResource.class.isAnnotationPresent(Singleton.class)</code>\u4e3a\u771f\u3002 */
    @Test
    void resourcesAreApplicationScoped() {
        assertTrue(GoodsQueryResource.class.isAnnotationPresent(ApplicationScoped.class));
        assertTrue(CatalogTradeResource.class.isAnnotationPresent(Singleton.class));
    }
}
