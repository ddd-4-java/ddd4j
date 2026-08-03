package io.ddd4j.quarkus.health;

import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Ddd4jQuarkusReadinessHealthCheckTest {

    @Test
    void shouldMapReadyAndUnavailableReportsWithoutExposingFailureDetails() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry()
                .register(() -> ReadinessResult.ready("database"));
        Ddd4jQuarkusReadinessHealthCheck healthCheck = new Ddd4jQuarkusReadinessHealthCheck(registry);

        HealthCheckResponse ready = healthCheck.call();

        assertEquals(HealthCheckResponse.Status.UP, ready.getStatus());
        assertEquals(1L, ready.getData().orElseThrow().get("checks"));

        registry.register(() -> ReadinessResult.unavailable("redis", "secret connection error"));
        HealthCheckResponse unavailable = healthCheck.call();

        assertEquals(HealthCheckResponse.Status.DOWN, unavailable.getStatus());
        assertEquals(2L, unavailable.getData().orElseThrow().get("checks"));
    }
}
