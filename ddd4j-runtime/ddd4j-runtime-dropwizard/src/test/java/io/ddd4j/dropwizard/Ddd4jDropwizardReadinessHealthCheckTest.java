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
package io.ddd4j.dropwizard;

import com.codahale.metrics.health.HealthCheck;
import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
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
