package io.ddd4j.helidon.health;

import io.ddd4j.core.health.ReadinessReport;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

    private final RuntimeReadinessRegistry readinessRegistry;

    @Inject
    public Ddd4jHelidonReadinessHealthCheck(RuntimeReadinessRegistry readinessRegistry) {
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    @Override
    public HealthCheckResponse call() {
        ReadinessReport report = readinessRegistry.readiness();
        HealthCheckResponseBuilder response = HealthCheckResponse.named("ddd4j").withData("checks",
                report.results().size());
        return report.ready() ? response.up().build() : response.down().build();
    }
}
