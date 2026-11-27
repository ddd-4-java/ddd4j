package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通过 <code>ZonedDateTime.parse()</code>、<code>ProjectionCron.parse()</code>、<code>sunday.plusSeconds()</code> 验证 <code>ProjectionCron</code> 的运行行为。
 */
class ProjectionCronTest {
    /**
     * 验证<code>ProjectionCron.parse("0/5 * * * * *").next(sunday)</code> 等于 <code>sunday.plusSeconds(5)</code>，<code>ProjectionCron.parse("1,3 0-5 8 * * 0").next(sunday)</code> 等于 <code>sunday.plusSeconds(1)</code>，<code>ProjectionCron.parse("0 0 9 ? * 1-5").next(sunday)</code> 等于 <code>ZonedDateTime.parse("2026-11-16T09:00:00+08:00[Asia/Shanghai]")</code>。
     */
    @Test
    void numericSixFieldContractSupportsStepsListsRangesAndSundayAliases() {
        ZonedDateTime sunday = ZonedDateTime.parse("2026-11-15T08:00:00+08:00[Asia/Shanghai]");
        assertThat(ProjectionCron.parse("0/5 * * * * *").next(sunday)).isEqualTo(sunday.plusSeconds(5));
        assertThat(ProjectionCron.parse("1,3 0-5 8 * * 0").next(sunday)).isEqualTo(sunday.plusSeconds(1));
        assertThat(ProjectionCron.parse("1,3 0-5 8 * * 7").next(sunday)).isEqualTo(sunday.plusSeconds(1));
        assertThat(ProjectionCron.parse("0 0 9 ? * 1-5").next(sunday))
                .isEqualTo(ZonedDateTime.parse("2026-11-16T09:00:00+08:00[Asia/Shanghai]"));
    }

    /**
     * 验证<code>cron.next(ZonedDateTime.parse("2026-03-08T00:00:00-05:00[America/New_York]"))</code> 等于 <code>ZonedDateTime.parse("2026-03-09T02:30:00-04:00[America/New_York]")</code>，<code>cron.next(ZonedDateTime.parse("2026-03-08T00:00:00+08:00[Asia/Shanghai]"))</code> 等于 <code>ZonedDateTime.parse("2026-03-08T02:30:00+08:00[Asia/Shanghai]")</code>。
     */
    @Test
    void nextUsesSuppliedZoneAndSkipsNonexistentDstTime() {
        ProjectionCron cron = ProjectionCron.parse("0 30 2 * * *");
        assertThat(cron.next(ZonedDateTime.parse("2026-03-08T00:00:00-05:00[America/New_York]")))
                .isEqualTo(ZonedDateTime.parse("2026-03-09T02:30:00-04:00[America/New_York]"));
        assertThat(cron.next(ZonedDateTime.parse("2026-03-08T00:00:00+08:00[Asia/Shanghai]")))
                .isEqualTo(ZonedDateTime.parse("2026-03-08T02:30:00+08:00[Asia/Shanghai]"));
    }

    /**
     * 验证<code>ProjectionCron.parse("0 0 0 1 1 *").next(ZonedDateTime.parse("2026-01-02T00:00:00Z"))</code> 等于 <code>ZonedDateTime.parse("2027-01-01T00:00:00Z")</code>。
     */
    @Test
    void nextIncludesRepeatedDstHourAndSchedulesBeyondOldMillionSecondWindow() {
        ProjectionCron overlap = ProjectionCron.parse("0 30 1 * * *");
        assertThat(overlap.next(ZonedDateTime.parse("2026-11-01T01:30:00-04:00[America/New_York]").withEarlierOffsetAtOverlap()))
                .isEqualTo(ZonedDateTime.parse("2026-11-01T01:30:00-05:00[America/New_York]").withLaterOffsetAtOverlap());
        assertThat(ProjectionCron.parse("0 0 0 1 1 *").next(ZonedDateTime.parse("2026-01-02T00:00:00Z")))
                .isEqualTo(ZonedDateTime.parse("2027-01-01T00:00:00Z"));
    }

    /**
     * 验证<code>ProjectionCron.parse("5/120 * * * * *").next(from)</code> 等于 <code>from.plusSeconds(5)</code>，<code>ProjectionCron.parse("0/120,5/120,59/2147483647 * * * * *").next(from)</code> 等于 <code>from.plusSeconds(5)</code>，<code>ProjectionCron.parse("0 0 0 *&#47;120 * *").next(from)</code> 等于 <code>ZonedDateTime.parse("2026-12-01T00:00:00Z")</code>。
     */
    @Test
    void positiveStepsWiderThanFieldKeepTheStartAndCannotOverflow() {
        ZonedDateTime from = ZonedDateTime.parse("2026-11-18T12:00:00Z");
        for (String field : new String[]{"*/60", "*/120", "0/120", "*/2147483647"}) {
            assertThat(ProjectionCron.parse(field + " * * * * *").next(from))
                    .as(field).isEqualTo(from.plusMinutes(1));
        }
        assertThat(ProjectionCron.parse("5/120 * * * * *").next(from)).isEqualTo(from.plusSeconds(5));
        assertThat(ProjectionCron.parse("0/120,5/120,59/2147483647 * * * * *").next(from))
                .isEqualTo(from.plusSeconds(5));
        assertThat(ProjectionCron.parse("0/120,5/120,59/2147483647 * * * * *").next(from.plusSeconds(5)))
                .isEqualTo(from.plusSeconds(59));
        assertThat(ProjectionCron.parse("58/2147483646 * * * * *").next(from))
                .isEqualTo(from.plusSeconds(58));
        assertThat(ProjectionCron.parse("59/2147483647 * * * * *").next(from.plusSeconds(59)))
                .isEqualTo(from.plusMinutes(1).plusSeconds(59));
        // 非零字段最小值同样保留起点，避免只修秒字段。
        assertThat(ProjectionCron.parse("0 0 0 */120 * *").next(from))
                .isEqualTo(ZonedDateTime.parse("2026-12-01T00:00:00Z"));
    }

    /**
     * 验证<code>ProjectionCron.parse(expression)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void invalidFieldsFailInsteadOfHangingOrBeingSilentlyInterpreted() {
        for (String expression : new String[]{"*/0 * * * * *", "*/-120 * * * * *", "60/120 * * * * *",
                "*/2147483648 * * * * *", "-1/120 * * * * *", "60 * * * * *", "* * * * *",
                "* * * * * * *", "? * * * * *", "* * * * * MON", "1-0 * * * * *", "* * * * * 1,"}) {
            assertThatThrownBy(() -> ProjectionCron.parse(expression))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
