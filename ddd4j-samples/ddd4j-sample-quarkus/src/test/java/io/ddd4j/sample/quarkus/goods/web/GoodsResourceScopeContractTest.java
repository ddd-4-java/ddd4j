package io.ddd4j.sample.quarkus.goods.web;

import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品 REST 资源在三条兼容线使用相同的 CDI 生命周期。
 */
class GoodsResourceScopeContractTest {

    /**
     * 验证<code>GoodsResource.class.getAnnotation(ApplicationScoped.class)</code>非空。
     */
    @Test
    void resourceIsApplicationScoped() {
        assertThat(GoodsResource.class.getAnnotation(ApplicationScoped.class)).isNotNull();
    }
}
