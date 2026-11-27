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
import io.opentelemetry.api.trace.Tracer;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CacheSpan} 缓存操作 span 测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class CacheSpanTest {

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>"value-123"</code>\uff0c<code>executed</code>\u4e3a\u771f\u3002 */
    @Test
    void instrument_readOperation_shouldExecuteSupplier() {
        AtomicBoolean executed = new AtomicBoolean(false);
        String result = CacheSpan.instrument("userCache", "redis",
                () -> {
                    executed.set(true);
                    return "value-123";
                },
                () -> false);

        assertThat(result).isEqualTo("value-123");
        assertThat(executed).isTrue();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void instrument_cacheMiss_shouldStillExecuteSupplier() {
        String result = CacheSpan.instrument("userCache", "redis",
                () -> null,
                () -> true);

        assertThat(result).isNull();
    }

    /** \u9a8c\u8bc1<code>executed</code>\u4e3a\u771f\u3002 */
    @Test
    void instrument_writeOperation_shouldExecuteRunnable() {
        AtomicBoolean executed = new AtomicBoolean(false);
        CacheSpan.instrument("userCache", "redis", "put", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    /** \u9a8c\u8bc1<code>executed</code>\u4e3a\u771f\u3002 */
    @Test
    void instrument_evictOperation_shouldExecuteRunnable() {
        AtomicBoolean executed = new AtomicBoolean(false);
        CacheSpan.instrument("userCache", "redis", "evict", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void instrument_throwingException_shouldPropagateException() {
        assertThatThrownBy(() -> CacheSpan.instrument("userCache", "redis", () -> {
            throw new IllegalStateException("cache failure");
        }, () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cache failure");
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\u3002 */
    @Test
    void instrument_writeOperation_throwingException_shouldPropagate() {
        assertThatThrownBy(() -> CacheSpan.instrument("userCache", "redis", "put", () -> {
            throw new RuntimeException("write failed");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("write failed");
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>"value"</code>\uff0c<code>executed</code>\u4e3a\u771f\u3002 */
    @Test
    void instrument_whenOtelNotAvailable_shouldExecuteWithoutSpan() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());

        AtomicBoolean executed = new AtomicBoolean(false);
        String result = CacheSpan.instrument("userCache", "redis",
                () -> {
                    executed.set(true);
                    return "value";
                },
                () -> false);

        assertThat(result).isEqualTo("value");
        assertThat(executed).isTrue();
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>"value"</code>\u3002 */
    @Test
    void instrument_withOtelAvailable_shouldNotThrow() {
        // 提供一个 OpenTelemetry 实例（noop SDK + 真实 Tracer）
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());

        String result = CacheSpan.instrument("userCache", "redis",
                () -> "value", () -> false);

        assertThat(result).isEqualTo("value");
    }
}
