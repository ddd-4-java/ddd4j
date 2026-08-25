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
package io.ddd4j.metrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OpenTelemetryProjectionMetrics} 测试。
 *
 * <p>使用 OTel SDK Testing 的 {@link InMemoryMetricReader} 进行真实指标断言。
 *
 * @author PartMe.AI
 */
@DisplayName("OpenTelemetryProjectionMetrics")
class OpenTelemetryProjectionMetricsTest {

    private InMemoryMetricReader metricReader;
    private SdkMeterProvider meterProvider;
    private Meter meter;
    private OpenTelemetryProjectionMetrics metrics;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();
        meter = meterProvider.get("test-scope");
        metrics = new OpenTelemetryProjectionMetrics(meter);
    }

    @Test
    @DisplayName("构造：null Meter 应抛 NPE")
    void constructor_nullMeter_shouldThrowNPE() {
        assertThatThrownBy(() -> new OpenTelemetryProjectionMetrics((Meter) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("便捷构造：null OpenTelemetry 应抛 NPE")
    void convenienceConstructor_nullOpenTelemetry_shouldThrowNPE() {
        assertThatThrownBy(() -> new OpenTelemetryProjectionMetrics((OpenTelemetry) null, "test"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("便捷构造：null instrumentationScope 应抛 NPE")
    void convenienceConstructor_nullScope_shouldThrowNPE() {
        assertThatThrownBy(() -> new OpenTelemetryProjectionMetrics(OpenTelemetry.noop(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("onRunStarted")
    class OnRunStartedTests {

        @Test
        @DisplayName("应记录运行次数计数器")
        void shouldRecordRunCounter() {
            metrics.onRunStarted("orders");

            List<MetricData> metrics = collectMetricsByName(OpenTelemetryProjectionMetrics.METRIC_RUN_COUNT);
            assertThat(metrics).hasSize(1);
            assertThat(metrics.get(0).getLongSumData().getPoints())
                    .hasSize(1)
                    .first()
                    .satisfies(point -> {
                        assertThat(point.getValue()).isEqualTo(1);
                        assertThat(point.getAttributes().get(AttributeKey.stringKey("streamId"))).isEqualTo("orders");
                    });
        }

        @Test
        @DisplayName("多次调用应累加")
        void multipleCalls_shouldAccumulate() {
            metrics.onRunStarted("orders");
            metrics.onRunStarted("orders");

            List<MetricData> metrics = collectMetricsByName(OpenTelemetryProjectionMetrics.METRIC_RUN_COUNT);
            assertThat(metrics.get(0).getLongSumData().getPoints())
                    .first()
                    .satisfies(point -> assertThat(point.getValue()).isEqualTo(2));
        }

        @Test
        @DisplayName("不同 streamId 应独立计数")
        void differentStreamIds_shouldBeIndependent() {
            metrics.onRunStarted("orders");
            metrics.onRunStarted("payments");

            List<MetricData> metrics = collectMetricsByName(OpenTelemetryProjectionMetrics.METRIC_RUN_COUNT);
            assertThat(metrics.get(0).getLongSumData().getPoints())
                    .hasSize(2)
                    .extracting(point -> point.getAttributes().get(AttributeKey.stringKey("streamId")))
                    .containsExactlyInAnyOrder("orders", "payments");
        }
    }

    @Nested
    @DisplayName("onRunCompleted")
    class OnRunCompletedTests {

        @Test
        @DisplayName("应记录事件计数器和运行耗时")
        void shouldRecordEventCounterAndDuration() {
            metrics.onRunCompleted("orders", 10, 5_000_000L, 10);

            // 事件计数
            List<MetricData> eventMetrics = collectMetricsByName(OpenTelemetryProjectionMetrics.METRIC_EVENT_COUNT);
            assertThat(eventMetrics).hasSize(1);
            assertThat(eventMetrics.get(0).getLongSumData().getPoints())
                    .first()
                    .satisfies(point -> {
                        assertThat(point.getValue()).isEqualTo(10);
                        assertThat(point.getAttributes().get(AttributeKey.stringKey("streamId"))).isEqualTo("orders");
                    });

            // 运行耗时（5ms）
            List<MetricData> durationMetrics = collectMetricsByName(OpenTelemetryProjectionMetrics.METRIC_RUN_DURATION);
            assertThat(durationMetrics).hasSize(1);
            assertThat(durationMetrics.get(0).getHistogramData().getPoints())
                    .first()
                    .satisfies(point -> {
                        assertThat(point.getSum()).isEqualTo(5.0);
                        assertThat(point.getAttributes().get(AttributeKey.stringKey("streamId"))).isEqualTo("orders");
                    });
        }

        @Test
        @DisplayName("多次调用应累加")
        void multipleCalls_shouldAccumulate() {
            metrics.onRunCompleted("orders", 5, 1_000_000L, 5);
            metrics.onRunCompleted("orders", 3, 2_000_000L, 3);

            List<MetricData> eventMetrics = collectMetricsByName(OpenTelemetryProjectionMetrics.METRIC_EVENT_COUNT);
            assertThat(eventMetrics.get(0).getLongSumData().getPoints())
                    .first()
                    .satisfies(point -> assertThat(point.getValue()).isEqualTo(8));

            List<MetricData> durationMetrics = collectMetricsByName(OpenTelemetryProjectionMetrics.METRIC_RUN_DURATION);
            assertThat(durationMetrics.get(0).getHistogramData().getPoints())
                    .first()
                    .satisfies(point -> assertThat(point.getSum()).isEqualTo(3.0));
        }
    }

    @Nested
    @DisplayName("onRunFailed")
    class OnRunFailedTests {

        @Test
        @DisplayName("应记录错误计数器")
        void shouldRecordErrorCounter() {
            metrics.onRunFailed("orders", new RuntimeException("boom"));

            List<MetricData> errorMetrics = collectMetricsByName(OpenTelemetryProjectionMetrics.METRIC_RUN_ERROR);
            assertThat(errorMetrics).hasSize(1);
            assertThat(errorMetrics.get(0).getLongSumData().getPoints())
                    .first()
                    .satisfies(point -> {
                        assertThat(point.getValue()).isEqualTo(1);
                        assertThat(point.getAttributes().get(AttributeKey.stringKey("streamId"))).isEqualTo("orders");
                    });
        }

        @Test
        @DisplayName("多次失败应累加")
        void multipleFailures_shouldAccumulate() {
            metrics.onRunFailed("orders", new RuntimeException("err1"));
            metrics.onRunFailed("orders", new RuntimeException("err2"));

            List<MetricData> errorMetrics = collectMetricsByName(OpenTelemetryProjectionMetrics.METRIC_RUN_ERROR);
            assertThat(errorMetrics.get(0).getLongSumData().getPoints())
                    .first()
                    .satisfies(point -> assertThat(point.getValue()).isEqualTo(2));
        }
    }

    /**
     * 按指标名称收集 MetricData。
     */
    private List<MetricData> collectMetricsByName(String metricName) {
        return metricReader.collectAllMetrics().stream()
                .filter(md -> md.getName().equals(metricName))
                .collect(Collectors.toList());
    }
}
