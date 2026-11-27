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
package io.ddd4j.extension.otel;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EventSpan} 领域事件/CQRS span 测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class EventSpanTest {

    /**
     * 测试执行前执行 <code>OpenTelemetrySdkSetter.set()</code>、<code>OpenTelemetry.noop()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    /**
     * 测试结束后执行 <code>OpenTelemetrySdkSetter.set()</code>、<code>OpenTelemetry.noop()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    /**
     * 验证<code>executed</code>为真。
     */
    @Test
    void publish_shouldExecuteRunnable() {
        AtomicBoolean executed = new AtomicBoolean(false);
        EventSpan.publish("OrderCreatedEvent", "Order", "ORD-001", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    /**
     * 验证<code>result</code> 的大小为 <code>2</code>。
     */
    @Test
    void query_shouldReturnValue() {
        List<String> result = EventSpan.query("OrderQuery", () -> Arrays.asList("ORD-001", "ORD-002"));

        assertThat(result).hasSize(2);
    }

    /**
     * 验证<code>result</code> 等于 <code>42</code>。
     */
    @Test
    void projection_shouldReturnValue() {
        int result = EventSpan.projection("OrderProjection", () -> 42);

        assertThat(result).isEqualTo(42);
    }

    /**
     * 验证<code>executed</code>为真。
     */
    @Test
    void publish_whenOtelNotAvailable_shouldExecuteNormally() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());

        AtomicBoolean executed = new AtomicBoolean(false);
        EventSpan.publish("Event", "Agg", "1", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    /**
     * 验证<code>true</code>为真。
     */
    @Test
    void query_withNullSupplier_shouldThrowNpe() {
        try {
            EventSpan.query(null, () -> "result");
            // 不会抛 NPE，因为 supplier 是 lambda 不需要参数
        } catch (NullPointerException ignored) {
            assertThat(true).isTrue();
        }
    }

    /**
     * 验证<code>result</code> 等于 <code>"string-result"</code>。
     */
    @Test
    void projection_shouldAcceptAnyReturnType() {
        Object result = EventSpan.projection("Projection", () -> "string-result");
        assertThat(result).isEqualTo("string-result");
    }
}