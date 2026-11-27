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
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Map;
import java.util.Objects;

/**
 * 将 ddd4j 依赖就绪状态映射到 Micronaut 管理端点。
 */
@Singleton
@Requires(beans = RuntimeReadinessRegistry.class)
public class Ddd4jMicronautReadinessHealthIndicator implements HealthIndicator {

    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * 使用 ddd4j 运行时就绪注册表创建健康指标。
     *
     * @param readinessRegistry 就绪注册表
     */
    public Ddd4jMicronautReadinessHealthIndicator(RuntimeReadinessRegistry readinessRegistry) {
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    /**
     * \u8ba1\u7b97\u5e94\u7528\u5c31\u7eea\u72b6\u6001\u5e76\u901a\u8fc7 Micronaut \u6307\u6807\u5165\u53e3\u53d1\u5e03\u3002
     * @return \u53d1\u51fa\u5f53\u524d\u8fd0\u884c\u65f6\u5065\u5eb7\u72b6\u6001\u7684\u54cd\u5e94\u5f0f\u7ed3\u679c
     */
    @Override
    public Publisher<HealthResult> getResult() {
        ReadinessReport report = readinessRegistry.readiness();
        HealthStatus status = report.ready() ? HealthStatus.UP : HealthStatus.DOWN;
        HealthResult result = HealthResult.builder("ddd4j", status)
                .details(Map.of("checks", report.results().size()))
                .build();
        return Publishers.just(result);
    }
}
