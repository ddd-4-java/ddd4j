package io.ddd4j.extension.otel;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SqlSpan} SQL span 测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class SqlSpanTest {

    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @Test
    void execute_shouldExecuteRunnable() {
        AtomicBoolean executed = new AtomicBoolean(false);
        SqlSpan.execute("mybatis", "SELECT * FROM orders", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    @Test
    void execute_withSupplier_shouldReturnValue() {
        String result = SqlSpan.execute("jpa", "SELECT u FROM User u", () -> "user-data");

        assertThat(result).isEqualTo("user-data");
    }

    @Test
    void execute_withNullStatement_shouldNotThrow() {
        SqlSpan.execute("mybatis", null, () -> {
        });
        assertThat(true).isTrue();
    }

    @Test
    void execute_withVeryLongStatement_shouldNotThrow() {
        String longSql = "SELECT * FROM huge_table WHERE " + "a = 1 AND ".repeat(100) + "b = 2";
        SqlSpan.execute("mybatis", longSql, () -> {
        });
        assertThat(true).isTrue();
    }

    @Test
    void execute_whenOtelNotAvailable_shouldStillExecute() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());

        AtomicBoolean executed = new AtomicBoolean(false);
        SqlSpan.execute("mybatis", "SELECT 1", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    @Test
    void execute_onException_shouldPropagateException() {
        assertThatThrownBy(() -> SqlSpan.execute("mybatis", "SELECT 1", () -> {
            throw new RuntimeException("SQL error");
        })).isInstanceOf(RuntimeException.class)
                .hasMessage("SQL error");
    }

    @Test
    void execute_supplierOnException_shouldPropagateException() {
        assertThatThrownBy(() -> SqlSpan.execute("mybatis", "SELECT 1", () -> {
            throw new RuntimeException("SQL error supplier");
        })).isInstanceOf(RuntimeException.class)
                .hasMessage("SQL error supplier");
    }
}