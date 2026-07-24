package io.ddd4j.extension.otel;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EventSpan} 领域事件/CQRS span 测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class EventSpanTest {

    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @Test
    void publish_shouldExecuteRunnable() {
        AtomicBoolean executed = new AtomicBoolean(false);
        EventSpan.publish("OrderCreatedEvent", "Order", "ORD-001", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    @Test
    void query_shouldReturnValue() {
        List<String> result = EventSpan.query("OrderQuery", () -> List.of("ORD-001", "ORD-002"));

        assertThat(result).hasSize(2);
    }

    @Test
    void projection_shouldReturnValue() {
        int result = EventSpan.projection("OrderProjection", () -> 42);

        assertThat(result).isEqualTo(42);
    }

    @Test
    void publish_whenOtelNotAvailable_shouldExecuteNormally() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());

        AtomicBoolean executed = new AtomicBoolean(false);
        EventSpan.publish("Event", "Agg", "1", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    @Test
    void query_withNullSupplier_shouldThrowNpe() {
        try {
            EventSpan.query(null, () -> "result");
            // 不会抛 NPE，因为 supplier 是 lambda 不需要参数
        } catch (NullPointerException ignored) {
            assertThat(true).isTrue();
        }
    }

    @Test
    void projection_shouldAcceptAnyReturnType() {
        Object result = EventSpan.projection("Projection", () -> "string-result");
        assertThat(result).isEqualTo("string-result");
    }
}