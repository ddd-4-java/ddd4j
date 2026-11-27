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

    /** \u8bfb\u53d6\u6d4b\u8bd5\u671f\u95f4\u4ea7\u751f\u7684\u6307\u6807\u5feb\u7167\u3002 */
    private InMemoryMetricReader metricReader;
    /** \u7ed1\u5b9a\u5185\u5b58\u6307\u6807\u8bfb\u53d6\u5668\u7684 OpenTelemetry SDK \u63d0\u4f9b\u5668\u3002 */
    private SdkMeterProvider meterProvider;
    /** \u4f9b\u88ab\u6d4b\u6295\u5f71\u6307\u6807\u9002\u914d\u5668\u6ce8\u518c\u6307\u6807\u7684 Meter\u3002 */
    private Meter meter;
    /** \u5f53\u524d\u7528\u4f8b\u4f7f\u7528\u7684 OpenTelemetry \u6295\u5f71\u6307\u6807\u9002\u914d\u5668\u3002 */
    private OpenTelemetryProjectionMetrics metrics;

    /** \u521b\u5efa\u9694\u79bb\u7684 Meter \u63d0\u4f9b\u5668\u3001\u6307\u6807\u8bfb\u53d6\u5668\u548c\u6295\u5f71\u6307\u6807\u9002\u914d\u5668\u3002 */
    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();
        meter = meterProvider.get("test-scope");
        metrics = new OpenTelemetryProjectionMetrics(meter);
    }

    /** \u6784\u9020\uff1anull Meter \u5e94\u629b NPE\u3002 */
    @Test
    @DisplayName("构造：null Meter 应抛 NPE")
    void constructor_nullMeter_shouldThrowNPE() {
        assertThatThrownBy(() -> new OpenTelemetryProjectionMetrics((Meter) null))
                .isInstanceOf(NullPointerException.class);
    }

    /** \u4fbf\u6377\u6784\u9020\uff1anull OpenTelemetry \u5e94\u629b NPE\u3002 */
    @Test
    @DisplayName("便捷构造：null OpenTelemetry 应抛 NPE")
    void convenienceConstructor_nullOpenTelemetry_shouldThrowNPE() {
        assertThatThrownBy(() -> new OpenTelemetryProjectionMetrics((OpenTelemetry) null, "test"))
                .isInstanceOf(NullPointerException.class);
    }

    /** \u4fbf\u6377\u6784\u9020\uff1anull instrumentationScope \u5e94\u629b NPE\u3002 */
    @Test
    @DisplayName("便捷构造：null instrumentationScope 应抛 NPE")
    void convenienceConstructor_nullScope_shouldThrowNPE() {
        assertThatThrownBy(() -> new OpenTelemetryProjectionMetrics(OpenTelemetry.noop(), null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * \u6309\u6307\u6807\u540d\u79f0\u6536\u96c6 MetricData\u3002
     *
     * @param metricName \u8981\u7b5b\u9009\u7684\u6307\u6807\u540d\u79f0
     * @return \u540c\u540d\u6307\u6807\u7684\u91c7\u96c6\u7ed3\u679c
     */
    private List<MetricData> collectMetricsByName(String metricName) {
        return metricReader.collectAllMetrics().stream()
                .filter(md -> md.getName().equals(metricName))
                .collect(Collectors.toList());
    }

    /** \u9a8c\u8bc1\u6295\u5f71\u542f\u52a8\u8ba1\u6570\u3001\u91cd\u590d\u8c03\u7528\u7d2f\u52a0\u548c\u4e8b\u4ef6\u6d41\u95f4\u9694\u79bb\u3002 */
    @Nested
    @DisplayName("onRunStarted")
    class OnRunStartedTests {

        /** \u5e94\u8bb0\u5f55\u8fd0\u884c\u6b21\u6570\u8ba1\u6570\u5668\u3002 */
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

        /** \u591a\u6b21\u8c03\u7528\u5e94\u7d2f\u52a0\u3002 */
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

        /** \u4e0d\u540c streamId \u5e94\u72ec\u7acb\u8ba1\u6570\u3002 */
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

    /** \u9a8c\u8bc1\u6295\u5f71\u5b8c\u6210\u65f6\u7684\u4e8b\u4ef6\u8ba1\u6570\u4e0e\u5904\u7406\u8017\u65f6\u3002 */
    @Nested
    @DisplayName("onRunCompleted")
    class OnRunCompletedTests {

        /** \u5e94\u8bb0\u5f55\u4e8b\u4ef6\u8ba1\u6570\u5668\u548c\u8fd0\u884c\u8017\u65f6\u3002 */
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

        /** \u591a\u6b21\u8c03\u7528\u5e94\u7d2f\u52a0\u3002 */
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

    /** \u9a8c\u8bc1\u6295\u5f71\u9519\u8bef\u8ba1\u6570\u4ee5\u53ca\u591a\u6b21\u5931\u8d25\u7684\u7d2f\u8ba1\u7ed3\u679c\u3002 */
    @Nested
    @DisplayName("onRunFailed")
    class OnRunFailedTests {

        /** \u5e94\u8bb0\u5f55\u9519\u8bef\u8ba1\u6570\u5668\u3002 */
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

        /** \u591a\u6b21\u5931\u8d25\u5e94\u7d2f\u52a0\u3002 */
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
}
