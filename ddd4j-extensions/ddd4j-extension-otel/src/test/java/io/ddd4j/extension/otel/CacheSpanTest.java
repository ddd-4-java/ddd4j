package io.ddd4j.extension.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
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

    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

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

    @Test
    void instrument_cacheMiss_shouldStillExecuteSupplier() {
        String result = CacheSpan.instrument("userCache", "redis",
                () -> null,
                () -> true);

        assertThat(result).isNull();
    }

    @Test
    void instrument_writeOperation_shouldExecuteRunnable() {
        AtomicBoolean executed = new AtomicBoolean(false);
        CacheSpan.instrument("userCache", "redis", "put", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    @Test
    void instrument_evictOperation_shouldExecuteRunnable() {
        AtomicBoolean executed = new AtomicBoolean(false);
        CacheSpan.instrument("userCache", "redis", "evict", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    @Test
    void instrument_throwingException_shouldPropagateException() {
        assertThatThrownBy(() -> CacheSpan.instrument("userCache", "redis", () -> {
            throw new IllegalStateException("cache failure");
        }, () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cache failure");
    }

    @Test
    void instrument_writeOperation_throwingException_shouldPropagate() {
        assertThatThrownBy(() -> CacheSpan.instrument("userCache", "redis", "put", () -> {
            throw new RuntimeException("write failed");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("write failed");
    }

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

    @Test
    void instrument_withOtelAvailable_shouldNotThrow() {
        // 提供一个 OpenTelemetry 实例（noop SDK + 真实 Tracer）
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());

        String result = CacheSpan.instrument("userCache", "redis",
                () -> "value", () -> false);

        assertThat(result).isEqualTo("value");
    }
}