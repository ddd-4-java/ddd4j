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
