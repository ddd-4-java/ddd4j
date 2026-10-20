package io.ddd4j.sample.quarkus.satoken;

import io.ddd4j.sample.quarkus.satoken.goods.web.GoodsQueryResource;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class GoodsQueryCdiContractTest {

    @Test
    void goodsQueryResourceIsApplicationScoped() {
        assertEquals("ApplicationScoped", Arc.container().instance(GoodsQueryResource.class)
                .getBean().getScope().getSimpleName());
    }
}
