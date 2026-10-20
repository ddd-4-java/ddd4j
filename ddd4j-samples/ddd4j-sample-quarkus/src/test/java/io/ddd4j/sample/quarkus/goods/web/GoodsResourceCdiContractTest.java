package io.ddd4j.sample.quarkus.goods.web;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class GoodsResourceCdiContractTest {

    @Test
    void resourceIsDiscoveredWithApplicationScope() {
        assertEquals("ApplicationScoped", Arc.container().instance(GoodsResource.class)
                .getBean().getScope().getSimpleName());
    }
}
