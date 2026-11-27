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
package io.ddd4j.quarkus.health;

import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
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

        assertEquals(HealthCheckResponse.State.UP, ready.getState());
        assertEquals(1L, ready.getData().orElseThrow(AssertionError::new).get("checks"));

        registry.register(() -> ReadinessResult.unavailable("redis", "secret connection error"));
        HealthCheckResponse unavailable = healthCheck.call();

        assertEquals(HealthCheckResponse.State.DOWN, unavailable.getState());
        assertEquals(2L, unavailable.getData().orElseThrow(AssertionError::new).get("checks"));
    }
}
