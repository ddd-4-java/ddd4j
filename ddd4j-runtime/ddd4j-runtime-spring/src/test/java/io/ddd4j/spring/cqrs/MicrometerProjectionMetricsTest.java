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
package io.ddd4j.spring.cqrs;
import io.micrometer.core.instrument.MeterRegistry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MicrometerProjectionMetrics} 测试。
 *
 * @author PartMe.AI
 */
@DisplayName("MicrometerProjectionMetrics")
class MicrometerProjectionMetricsTest {

    /** \u5f53\u524d\u7528\u4f8b\u6ce8\u518c\u88ab\u6d4b\u7ec4\u4ef6\u548c\u89c2\u5bdf\u6307\u6807\u7684\u5bb9\u5668\u3002 */
    private SimpleMeterRegistry registry;
    /** \u5f53\u524d\u7528\u4f8b\u89c2\u5bdf\u6216\u9a71\u52a8\u7684\u6295\u5f71\u6307\u6807\u5b9e\u73b0\u3002 */
    private MicrometerProjectionMetrics metrics;

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>registry = new SimpleMeterRegistry()</code>\u3001<code>metrics = new MicrometerProjectionMetrics(registry)</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerProjectionMetrics(registry);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructor_nullRegistry_shouldThrowNPE() {
        assertThatThrownBy(() -> new MicrometerProjectionMetrics(null))
                .isInstanceOf(NullPointerException.class);
    }

    /** 针对 {@code onRunCompleted} 指标记录行为的嵌套测试组。 */
    @Nested
    @DisplayName("onRunCompleted")
    class OnRunCompletedTests {

        /** \u5e94\u8bb0\u5f55\u4e8b\u4ef6\u8ba1\u6570\u5668\u548c\u8fd0\u884c\u8017\u65f6\u3002 */
        @Test
        @DisplayName("应记录事件计数器和运行耗时")
        void shouldRecordEventCounterAndDuration() {
            metrics.onRunCompleted("orders", 10, 5_000_000L, 10);

            Counter eventCounter = registry.find("projection.events.total")
                    .tag("stream", "orders")
                    .counter();
            assertThat(eventCounter).isNotNull();
            assertThat(eventCounter.count()).isEqualTo(10.0);

            Timer durationTimer = registry.find("projection.run.duration")
                    .tag("stream", "orders")
                    .timer();
            assertThat(durationTimer).isNotNull();
            assertThat(durationTimer.count()).isEqualTo(1);
            assertThat(durationTimer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(5_000_000.0);
        }

        /** \u591a\u6b21\u8c03\u7528\u5e94\u7d2f\u52a0\u3002 */
        @Test
        @DisplayName("多次调用应累加")
        void multipleCalls_shouldAccumulate() {
            metrics.onRunCompleted("orders", 5, 1_000_000L, 5);
            metrics.onRunCompleted("orders", 3, 2_000_000L, 3);

            Counter eventCounter = registry.find("projection.events.total")
                    .tag("stream", "orders")
                    .counter();
            assertThat(eventCounter.count()).isEqualTo(8.0);

            Timer durationTimer = registry.find("projection.run.duration")
                    .tag("stream", "orders")
                    .timer();
            assertThat(durationTimer.count()).isEqualTo(2);
        }

        /** \u4e0d\u540c streamId \u5e94\u72ec\u7acb\u8ba1\u6570\u3002 */
        @Test
        @DisplayName("不同 streamId 应独立计数")
        void differentStreamIds_shouldBeIndependent() {
            metrics.onRunCompleted("orders", 10, 1_000_000L, 10);
            metrics.onRunCompleted("payments", 5, 2_000_000L, 5);

            Counter ordersCounter = registry.find("projection.events.total")
                    .tag("stream", "orders").counter();
            Counter paymentsCounter = registry.find("projection.events.total")
                    .tag("stream", "payments").counter();

            assertThat(ordersCounter.count()).isEqualTo(10.0);
            assertThat(paymentsCounter.count()).isEqualTo(5.0);
        }
    }

    /** 针对 {@code onRunFailed} 错误计数指标的嵌套测试组。 */
    @Nested
    @DisplayName("onRunFailed")
    class OnRunFailedTests {

        /** \u5e94\u8bb0\u5f55\u9519\u8bef\u8ba1\u6570\u5668\u3002 */
        @Test
        @DisplayName("应记录错误计数器")
        void shouldRecordErrorCounter() {
            metrics.onRunFailed("orders", new RuntimeException("boom"));

            Counter errorCounter = registry.find("projection.errors.total")
                    .tag("stream", "orders")
                    .counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }

        /** \u591a\u6b21\u5931\u8d25\u5e94\u7d2f\u52a0\u3002 */
        @Test
        @DisplayName("多次失败应累加")
        void multipleFailures_shouldAccumulate() {
            metrics.onRunFailed("orders", new RuntimeException("err1"));
            metrics.onRunFailed("orders", new RuntimeException("err2"));

            Counter errorCounter = registry.find("projection.errors.total")
                    .tag("stream", "orders")
                    .counter();
            assertThat(errorCounter.count()).isEqualTo(2.0);
        }
    }

    /** 针对 {@code onRunStarted} 空实现（no-op）行为的嵌套测试组。 */
    @Nested
    @DisplayName("onRunStarted")
    class OnRunStartedTests {

        /** onRunStarted \u4e0d\u4ea7\u751f\u6307\u6807\uff08\u9ed8\u8ba4 no-op\uff09\u3002 */
        @Test
        @DisplayName("onRunStarted 不产生指标（默认 no-op）")
        void shouldNotRecordAnything() {
            metrics.onRunStarted("orders");

            assertThat(registry.find("projection.events.total").counter()).isNull();
            assertThat(registry.find("projection.run.duration").timer()).isNull();
            assertThat(registry.find("projection.errors.total").counter()).isNull();
        }
    }
}
