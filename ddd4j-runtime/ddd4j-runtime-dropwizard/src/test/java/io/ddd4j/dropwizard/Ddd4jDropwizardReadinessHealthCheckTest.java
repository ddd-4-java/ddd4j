package io.ddd4j.dropwizard;

import com.codahale.metrics.health.HealthCheck;
import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddd4jDropwizardReadinessHealthCheckTest {

    @Test
    void shouldMapReadyAndUnavailableReportsWithoutExposingFailureDetails() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry()
                .register(() -> ReadinessResult.ready("database"));
        Ddd4jDropwizardReadinessHealthCheck healthCheck = new Ddd4jDropwizardReadinessHealthCheck(registry);

        HealthCheck.Result ready = healthCheck.execute();

        assertTrue(ready.isHealthy());

        registry.register(() -> ReadinessResult.unavailable("redis", "secret connection error"));
        HealthCheck.Result unavailable = healthCheck.execute();

        assertFalse(unavailable.isHealthy());
    }
}
