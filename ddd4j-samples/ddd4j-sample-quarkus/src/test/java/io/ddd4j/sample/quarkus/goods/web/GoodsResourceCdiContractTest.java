package io.ddd4j.sample.quarkus.goods.web;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 <code>GoodsResourceCdiContract</code> 在 <code>resourceIsDiscovered</code> 等场景中的行为。
 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(io.ddd4j.sample.quarkus.GoodsStandaloneProfile.class)
class GoodsResourceCdiContractTest {

    /**
     * 验证<code>Arc.container().instance(GoodsResource.class) .getBean().getScope().getSimpleName()</code> 等于 <code>"ApplicationScoped"</code>。
     */
    @Test
    void resourceIsDiscoveredWithApplicationScope() {
        assertEquals("ApplicationScoped", Arc.container().instance(GoodsResource.class)
                .getBean().getScope().getSimpleName());
    }
}
