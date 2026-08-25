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
package io.ddd4j.quarkus.cqrs;

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
@DisplayName("Quarkus MicrometerProjectionMetrics")
class MicrometerProjectionMetricsTest {

    private SimpleMeterRegistry registry;
    private MicrometerProjectionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerProjectionMetrics(registry);
    }

    @Test
    void constructor_nullRegistry_shouldThrowNPE() {
        assertThatThrownBy(() -> new MicrometerProjectionMetrics(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("onRunCompleted")
    class OnRunCompletedTests {

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

    @Nested
    @DisplayName("onRunFailed")
    class OnRunFailedTests {

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

    @Nested
    @DisplayName("onRunStarted")
    class OnRunStartedTests {

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
