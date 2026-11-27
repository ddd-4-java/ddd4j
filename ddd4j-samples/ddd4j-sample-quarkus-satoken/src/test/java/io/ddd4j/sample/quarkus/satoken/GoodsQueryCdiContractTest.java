package io.ddd4j.sample.quarkus.satoken;

import io.ddd4j.sample.quarkus.satoken.goods.web.GoodsQueryResource;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>Arc.container()</code> \u9a8c\u8bc1 <code>GoodsQueryCdiContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(io.ddd4j.sample.quarkus.satoken.GoodsStandaloneProfile.class)
class GoodsQueryCdiContractTest {

    /** \u9a8c\u8bc1<code>Arc.container().instance(GoodsQueryResource.class) .getBean().getScope().getSimpleName()</code> \u7b49\u4e8e <code>"ApplicationScoped"</code>\u3002 */
    @Test
    void goodsQueryResourceIsApplicationScoped() {
        assertEquals("ApplicationScoped", Arc.container().instance(GoodsQueryResource.class)
                .getBean().getScope().getSimpleName());
    }
}
