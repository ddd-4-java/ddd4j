package io.ddd4j.data.projection.panache;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>Arc.container()</code> 验证 <code>ProjectionRepositoryCdiContract</code> 的运行行为。
 */
@QuarkusTest
class ProjectionRepositoryCdiContractTest {

    /**
     * 在测试框架中执行 <code>Arc.container()</code>，验证该场景。
     */
    @Test
    void repositoryIsApplicationScoped() {
        assertEquals("ApplicationScoped", Arc.container()
                .instance(QuarkusProjectionPositionRepository.class)
                .getBean().getScope().getSimpleName());
    }
}
