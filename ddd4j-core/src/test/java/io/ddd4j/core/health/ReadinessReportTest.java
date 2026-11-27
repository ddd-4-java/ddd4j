package io.ddd4j.core.health;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** \u5c31\u7eea\u62a5\u544a\u7684\u5feb\u7167\u3001\u8bca\u65ad\u4fe1\u606f\u4e0e\u5931\u8d25\u8bed\u4e49\u5951\u7ea6\u6d4b\u8bd5\u3002 */
class ReadinessReportTest {

    /** \u9a8c\u8bc1<code>report.ready()</code>\u4e3a\u771f\uff0c<code>report.results()</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\u3002 */
    @Test
    void check_shouldReportReadyWhenAllContributorsAreReady() {
        ReadinessReport report = ReadinessReport.check(List.of(
                () -> ReadinessResult.ready("postgresql"),
                () -> ReadinessResult.ready("redis")));

        assertThat(report.ready()).isTrue();
        assertThat(report.results()).hasSize(2);
    }

    /** \u9a8c\u8bc1<code>report.ready()</code>\u4e3a\u5047\uff0c<code>report.results()</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>result.details()</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"reason"</code>\u3001<code>"check failed"</code>\u3002 */
    @Test
    void check_shouldFailClosedWhenContributorThrows() {
        ReadinessReport report = ReadinessReport.check(List.of(
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
