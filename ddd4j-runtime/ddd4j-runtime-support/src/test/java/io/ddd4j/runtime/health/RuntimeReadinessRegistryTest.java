package io.ddd4j.runtime.health;

import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.ReadinessResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeReadinessRegistryTest {

    @Test
    void shouldAggregateContributorsAndAllowRuntimeRegistrationChanges() {
        ReadinessContributor database = () -> ReadinessResult.ready("database");
        ReadinessContributor cache = () -> ReadinessResult.unavailable("cache", "unreachable");
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry(List.of(database));

        assertThat(registry.readiness().ready()).isTrue();

        registry.register(cache);
        assertThat(registry.readiness().ready()).isFalse();
        assertThat(registry.readiness().results()).extracting(ReadinessResult::name)
                .containsExactly("database", "cache");

        assertThat(registry.unregister(cache)).isTrue();
        assertThat(registry.readiness().ready()).isTrue();
    }
}
