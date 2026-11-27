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
package io.ddd4j.helidon.health;

import io.ddd4j.core.health.ReadinessReport;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.util.Objects;

/**
 * 将 ddd4j 依赖就绪状态映射到 Helidon MP Readiness。
 */
@Readiness
@ApplicationScoped
public class Ddd4jHelidonReadinessHealthCheck implements HealthCheck {

    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * \u4f7f\u7528 ddd4j \u5c31\u7eea\u6ce8\u518c\u8868\u521b\u5efa\u5065\u5eb7\u68c0\u67e5\u3002
     * @param readinessRegistry \u5c31\u7eea\u6ce8\u518c\u8868
     */
    @Inject
    public Ddd4jHelidonReadinessHealthCheck(RuntimeReadinessRegistry readinessRegistry) {
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    /**
     * \u5c06 DDD4J \u5c31\u7eea\u72b6\u6001\u8f6c\u6362\u4e3a Helidon \u5065\u5eb7\u68c0\u67e5\u54cd\u5e94\u3002
     * @return \u6846\u67b6\u5065\u5eb7\u7aef\u70b9\u8fd4\u56de\u7684\u5c31\u7eea\u7ed3\u679c
     */
    @Override
    public HealthCheckResponse call() {
        ReadinessReport report = readinessRegistry.readiness();
        HealthCheckResponseBuilder response = HealthCheckResponse.named("ddd4j").withData("checks",
                report.results().size());
        return report.ready() ? response.up().build() : response.down().build();
    }
}
