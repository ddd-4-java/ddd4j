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

    /** \u9a8c\u8bc1<code>executed</code>\u4e3a\u771f\u3002 */
    @Test
    void execute_shouldExecuteRunnable() {
        AtomicBoolean executed = new AtomicBoolean(false);
        SqlSpan.execute("mybatis", "SELECT * FROM orders", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>"user-data"</code>\u3002 */
    @Test
    void execute_withSupplier_shouldReturnValue() {
        String result = SqlSpan.execute("jpa", "SELECT u FROM User u", () -> "user-data");

        assertThat(result).isEqualTo("user-data");
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void execute_withNullStatement_shouldNotThrow() {
        SqlSpan.execute("mybatis", null, () -> {
        });
        assertThat(true).isTrue();
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void execute_withVeryLongStatement_shouldNotThrow() {
        String longSql = "SELECT * FROM huge_table WHERE " + "a = 1 AND ".repeat(100) + "b = 2";
        SqlSpan.execute("mybatis", longSql, () -> {
        });
        assertThat(true).isTrue();
    }

    /** \u9a8c\u8bc1<code>executed</code>\u4e3a\u771f\u3002 */
    @Test
    void execute_whenOtelNotAvailable_shouldStillExecute() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());

        AtomicBoolean executed = new AtomicBoolean(false);
        SqlSpan.execute("mybatis", "SELECT 1", () -> {
            executed.set(true);
        });

        assertThat(executed).isTrue();
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\u3002 */
    @Test
    void execute_onException_shouldPropagateException() {
        assertThatThrownBy(() -> SqlSpan.execute("mybatis", "SELECT 1", () -> {
            throw new RuntimeException("SQL error");
        })).isInstanceOf(RuntimeException.class)
                .hasMessage("SQL error");
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\u3002 */
    @Test
    void execute_supplierOnException_shouldPropagateException() {
        assertThatThrownBy(() -> SqlSpan.execute("mybatis", "SELECT 1", () -> {
            throw new RuntimeException("SQL error supplier");
        })).isInstanceOf(RuntimeException.class)
                .hasMessage("SQL error supplier");
    }
}