package io.ddd4j.helidon.health;

import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Ddd4jHelidonReadinessHealthCheckTest {

    @Test
    void shouldMapReadyAndUnavailableReportsWithoutExposingFailureDetails() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry()
                .register(() -> ReadinessResult.ready("database"));
        Ddd4jHelidonReadinessHealthCheck healthCheck = new Ddd4jHelidonReadinessHealthCheck(registry);

        HealthCheckResponse ready = healthCheck.call();

        assertEquals(HealthCheckResponse.State.UP, ready.getState());
        assertEquals(1L, ready.getData().get().get("checks"));

        registry.register(() -> ReadinessResult.unavailable("kafka", "secret broker error"));
        HealthCheckResponse unavailable = healthCheck.call();

        assertEquals(HealthCheckResponse.State.DOWN, unavailable.getState());
        assertEquals(2L, unavailable.getData().get().get("checks"));
    }
}
