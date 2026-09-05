/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

class Ddd4jMicronautReadinessHealthIndicatorTest {

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

    private HealthResult result(Ddd4jMicronautReadinessHealthIndicator healthIndicator) {
        AtomicReference<HealthResult> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        healthIndicator.getResult().subscribe(new Subscriber<HealthResult>() {
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
