package io.ddd4j.dropwizard;

import com.codahale.metrics.health.HealthCheck;
import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u9a8c\u8bc1 <code>Ddd4jDropwizardReadinessHealthCheck</code> \u5728<code>shouldMapReadyAndUnavailableReports</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jDropwizardReadinessHealthCheckTest {

    /** \u9a8c\u8bc1<code>ready.isHealthy()</code>\u4e3a\u771f\uff0c<code>unavailable.isHealthy()</code>\u4e3a\u5047\u3002 */
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
