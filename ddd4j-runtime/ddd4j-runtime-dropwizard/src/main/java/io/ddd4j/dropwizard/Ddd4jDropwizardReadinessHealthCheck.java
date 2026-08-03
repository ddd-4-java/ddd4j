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
