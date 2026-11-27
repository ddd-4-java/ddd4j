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
     * 使用 ddd4j 就绪注册表创建健康检查。
     *
     * @param readinessRegistry 就绪注册表
     */
    public Ddd4jDropwizardReadinessHealthCheck(RuntimeReadinessRegistry readinessRegistry) {
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d\u8fd0\u884c\u65f6\u5c31\u7eea\u62a5\u544a\u5e76\u8f6c\u6362\u4e3a Dropwizard \u5065\u5eb7\u7ed3\u679c\u3002
     * @return \u5e94\u7528\u7684\u5c31\u7eea\u5065\u5eb7\u68c0\u67e5\u7ed3\u679c
     */
    @Override
    protected Result check() {
        ReadinessReport report = readinessRegistry.readiness();
        return report.ready()
                ? Result.healthy("checks=%d", report.results().size())
                : Result.unhealthy("checks=%d", report.results().size());
    }
}
