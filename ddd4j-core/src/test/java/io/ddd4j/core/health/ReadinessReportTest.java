package io.ddd4j.core.health;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessReportTest {

    @Test
    void check_shouldReportReadyWhenAllContributorsAreReady() {
        ReadinessReport report = ReadinessReport.check(Arrays.asList(
                () -> ReadinessResult.ready("postgresql"),
                () -> ReadinessResult.ready("redis")));

        assertThat(report.ready()).isTrue();
        assertThat(report.results()).hasSize(2);
    }

    @Test
    void check_shouldFailClosedWhenContributorThrows() {
        ReadinessReport report = ReadinessReport.check(Arrays.asList(
                () -> ReadinessResult.ready("postgresql"),
                () -> {
                    throw new IllegalStateException("redis connection refused");
                }));

        assertThat(report.ready()).isFalse();
        assertThat(report.results()).hasSize(2);
        assertThat(report.results()).extracting(ReadinessResult::name).contains("postgresql");
        assertThat(report.results()).anySatisfy(result -> {
            assertThat(result.ready()).isFalse();
            assertThat(result.details()).containsEntry("reason", "check failed");
        });
    }
}
