package io.ddd4j.micronaut.health;

import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthResult;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** \u9a8c\u8bc1 <code>Ddd4jMicronautReadinessHealthIndicator</code> \u5728<code>shouldMapReadyAndUnavailableReports</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jMicronautReadinessHealthIndicatorTest {

    /** \u9a8c\u8bc1<code>ready.getStatus()</code> \u7b49\u4e8e <code>HealthStatus.UP</code>\uff0c<code>((Map&lt;?, ?&gt;) ready.getDetails()).get("checks")</code> \u7b49\u4e8e <code>1</code>\uff0c<code>unavailable.getDetails()</code> \u7b49\u4e8e <code>Collections.singletonMap("checks", 2)</code>\u3002 */
    @Test
    void shouldMapReadyAndUnavailableReportsWithoutExposingFailureDetails() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry()
                .register(() -> ReadinessResult.ready("database"));
        Ddd4jMicronautReadinessHealthIndicator healthIndicator =
                new Ddd4jMicronautReadinessHealthIndicator(registry);

        HealthResult ready = result(healthIndicator);

        assertEquals(HealthStatus.UP, ready.getStatus());
        assertEquals(1, ((Map<?, ?>) ready.getDetails()).get("checks"));

        registry.register(() -> ReadinessResult.unavailable("redis", "secret connection error"));
        HealthResult unavailable = result(healthIndicator);

        assertEquals(HealthStatus.DOWN, unavailable.getStatus());
        assertEquals(2, ((Map<?, ?>) unavailable.getDetails()).get("checks"));
    }

    /**
     * \u901a\u8fc7 <code>result.get()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param healthIndicator \u4f20\u7ed9 <code>healthIndicator.getResult()</code> \u7684 <code>Ddd4jMicronautReadinessHealthIndicator</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>result.get()</code>
     */
    private HealthResult result(Ddd4jMicronautReadinessHealthIndicator healthIndicator) {
        AtomicReference<HealthResult> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        healthIndicator.getResult().subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(HealthResult healthResult) {
                result.set(healthResult);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        assertNull(error.get());
        return result.get();
    }
}
