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

import io.ddd4j.core.health.ReadinessReport;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Map;
import java.util.Collections;
import java.util.Objects;

/**
 * 将 ddd4j 依赖就绪状态映射到 Micronaut 管理端点。
 */
@Singleton
@Requires(beans = RuntimeReadinessRegistry.class)
public class Ddd4jMicronautReadinessHealthIndicator implements HealthIndicator {

    private final RuntimeReadinessRegistry readinessRegistry;

    public Ddd4jMicronautReadinessHealthIndicator(RuntimeReadinessRegistry readinessRegistry) {
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    @Override
    public Publisher<HealthResult> getResult() {
        ReadinessReport report = readinessRegistry.readiness();
        HealthStatus status = report.ready() ? HealthStatus.UP : HealthStatus.DOWN;
        HealthResult result = HealthResult.builder("ddd4j", status)
                .details(Collections.singletonMap("checks", report.results().size()))
                .build();
        return Publishers.just(result);
    }
}
