package io.ddd4j.sample.quarkus.satoken;

import io.ddd4j.sample.quarkus.satoken.goods.web.GoodsQueryResource;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>Arc.container()</code> 验证 <code>GoodsQueryCdiContract</code> 的运行行为。
 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(io.ddd4j.sample.quarkus.satoken.GoodsStandaloneProfile.class)
class GoodsQueryCdiContractTest {

    /**
     * 验证<code>Arc.container().instance(GoodsQueryResource.class) .getBean().getScope().getSimpleName()</code> 等于 <code>"ApplicationScoped"</code>。
     */
    @Test
    void goodsQueryResourceIsApplicationScoped() {
        assertEquals("ApplicationScoped", Arc.container().instance(GoodsQueryResource.class)
                .getBean().getScope().getSimpleName());
    }
}
