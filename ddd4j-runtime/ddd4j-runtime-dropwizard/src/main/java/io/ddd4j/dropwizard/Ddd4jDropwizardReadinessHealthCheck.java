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
import io.ddd4j.core.health.ReadinessReport;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;

import java.util.Objects;

/**
 * 将 ddd4j 依赖就绪状态映射到 Dropwizard Admin HealthCheck。
 */
public class Ddd4jDropwizardReadinessHealthCheck extends HealthCheck {

    private final RuntimeReadinessRegistry readinessRegistry;

    public Ddd4jDropwizardReadinessHealthCheck(RuntimeReadinessRegistry readinessRegistry) {
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    @Override
    protected Result check() {
        ReadinessReport report = readinessRegistry.readiness();
        return report.ready()
                ? Result.healthy("checks=%d", report.results().size())
                : Result.unhealthy("checks=%d", report.results().size());
    }
}
