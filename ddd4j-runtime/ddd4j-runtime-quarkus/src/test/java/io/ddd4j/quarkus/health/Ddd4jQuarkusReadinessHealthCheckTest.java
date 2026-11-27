package io.ddd4j.quarkus.health;

import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u9a8c\u8bc1 <code>Ddd4jQuarkusReadinessHealthCheck</code> \u5728<code>shouldMapReadyAndUnavailableReports</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jQuarkusReadinessHealthCheckTest {

    /** \u9a8c\u8bc1<code>ready.getStatus()</code> \u7b49\u4e8e <code>HealthCheckResponse.Status.UP</code>\uff0c<code>ready.getData().orElseThrow().get("checks")</code> \u7b49\u4e8e <code>1L</code>\uff0c<code>unavailable.getData().orElseThrow().get("checks")</code> \u7b49\u4e8e <code>2L</code>\u3002 */
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
