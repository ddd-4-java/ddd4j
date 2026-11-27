package io.ddd4j.data.projection.panache;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>Arc.container()</code> \u9a8c\u8bc1 <code>ProjectionRepositoryCdiContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@QuarkusTest
class ProjectionRepositoryCdiContractTest {

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arc.container()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void repositoryIsApplicationScoped() {
        assertEquals("ApplicationScoped", Arc.container()
                .instance(QuarkusProjectionPositionRepository.class)
                .getBean().getScope().getSimpleName());
    }
}
