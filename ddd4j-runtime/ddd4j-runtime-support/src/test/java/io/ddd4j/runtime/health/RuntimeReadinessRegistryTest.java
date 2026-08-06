package io.ddd4j.runtime.health;

import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.ReadinessResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldFilterNullCollectionAndElementsInRegisterAll() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry();

        assertThat(registry.registerAll(null)).isSameAs(registry);
        List<ReadinessContributor> withNull = new ArrayList<>(Arrays.asList(
                () -> ReadinessResult.ready("a"), null));
        assertThat(registry.registerAll(withNull)).isSameAs(registry);

        assertThat(registry.contributors()).hasSize(1);
    }

    @Test
    void shouldRejectNullContributorOnRegister() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry();
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldReturnImmutableSnapshotOfContributors() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry(
                List.of(() -> ReadinessResult.ready("a")));

        List<ReadinessContributor> snapshot = registry.contributors();
        registry.register(() -> ReadinessResult.ready("b"));

        assertThat(snapshot).hasSize(1);
        assertThatThrownBy(() -> snapshot.add(() -> ReadinessResult.ready("c")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAggregateUnavailableContributorAsUnready() {
        RuntimeReadinessRegistry registry = new RuntimeReadinessRegistry(
                List.of(() -> ReadinessResult.unavailable("db", "not connected")));

        assertThat(registry.readiness().ready()).isFalse();
        assertThat(registry.readiness().results()).hasSize(1);
    }
}
