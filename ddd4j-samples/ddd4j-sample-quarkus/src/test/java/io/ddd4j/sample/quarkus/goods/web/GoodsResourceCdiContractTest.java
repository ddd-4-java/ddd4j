package io.ddd4j.sample.quarkus.goods.web;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u9a8c\u8bc1 <code>GoodsResourceCdiContract</code> \u5728 <code>resourceIsDiscovered</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(io.ddd4j.sample.quarkus.GoodsStandaloneProfile.class)
class GoodsResourceCdiContractTest {

    /** \u9a8c\u8bc1<code>Arc.container().instance(GoodsResource.class) .getBean().getScope().getSimpleName()</code> \u7b49\u4e8e <code>"ApplicationScoped"</code>\u3002 */
    @Test
    void resourceIsDiscoveredWithApplicationScope() {
        assertEquals("ApplicationScoped", Arc.container().instance(GoodsResource.class)
                .getBean().getScope().getSimpleName());
    }
}
