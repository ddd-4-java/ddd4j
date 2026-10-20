package io.ddd4j.data.projection.panache;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ProjectionRepositoryCdiContractTest {

    @Test
    void repositoryIsApplicationScoped() {
        assertEquals("ApplicationScoped", Arc.container()
                .instance(QuarkusProjectionPositionRepository.class)
                .getBean().getScope().getSimpleName());
    }
}
