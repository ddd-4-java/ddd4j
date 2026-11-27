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
import io.ddd4j.core.health.RuntimeReadinessRegistry;

import java.util.Objects;

/**
 * 将 ddd4j 依赖就绪状态映射到 Dropwizard Admin HealthCheck。
 */
public class Ddd4jDropwizardReadinessHealthCheck extends HealthCheck {

    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * \u4f7f\u7528 ddd4j \u5c31\u7eea\u6ce8\u518c\u8868\u521b\u5efa\u5065\u5eb7\u68c0\u67e5\u3002
     * @param readinessRegistry \u5c31\u7eea\u6ce8\u518c\u8868
     */
    public Ddd4jDropwizardReadinessHealthCheck(RuntimeReadinessRegistry readinessRegistry) {
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    /**
     * check() 方法。
     * @return 方法执行结果。
     */
    @Override
    protected Result check() {
        ReadinessReport report = readinessRegistry.readiness();
        return report.ready()
                ? Result.healthy("checks=%d", report.results().size())
                : Result.unhealthy("checks=%d", report.results().size());
    }
}
