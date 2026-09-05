package io.ddd4j.extension.otel;

import io.ddd4j.data.datascope.DataScopeProvider;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DataScopeSpan} 数据作用域评估 span 测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class DataScopeSpanTest {

    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @Test
    void evaluate_shouldReturnProviderResult() {
        DataScopeProvider provider = (dataType, data) -> true;
        boolean result = DataScopeSpan.evaluate("User", "user-123", provider);
        assertThat(result).isTrue();
    }

    @Test
    void evaluate_denyCase_shouldReturnFalse() {
        DataScopeProvider provider = (dataType, data) -> false;
        boolean result = DataScopeSpan.evaluate("Order", "ord-001", provider);
        assertThat(result).isFalse();
    }

    @Test
    void evaluate_nonNullAllowedProvider_shouldCheckNullability() {
        boolean result = DataScopeSpan.evaluate("Test", "valid-data",
                (dataType, data) -> java.util.Objects.nonNull(data));
        assertThat(result).isTrue();

        boolean nullResult = DataScopeSpan.evaluate("Test", null,
                (dataType, data) -> java.util.Objects.nonNull(data));
        assertThat(nullResult).isFalse();
    }

    @Test
    void evaluate_shouldCallProvider() {
        AtomicBoolean called = new AtomicBoolean(false);
        DataScopeProvider provider = (dataType, data) -> {
            called.set(true);
            return true;
        };

        DataScopeSpan.evaluate("Type", "value", provider);
        assertThat(called).isTrue();
    }

    @Test
    void evaluate_whenOtelNotAvailable_shouldStillCallProvider() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        AtomicBoolean called = new AtomicBoolean(false);
        DataScopeProvider provider = (dataType, data) -> {
            called.set(true);
            return true;
        };

        boolean result = DataScopeSpan.evaluate("Type", "value", provider);
        assertThat(result).isTrue();
        assertThat(called).isTrue();
    }

    @Test
    void evaluate_propagateException() {
        DataScopeProvider throwing = (dataType, data) -> {
            throw new IllegalStateException("scope evaluation failed");
        };

        assertThatThrownBy(() -> DataScopeSpan.evaluate("Type", "value", throwing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("scope evaluation failed");
    }

    @Test
    void evaluate_attributeKeys_shouldHaveCorrectNames() {
        assertThat(DataScopeSpan.ATTR_DATA_SCOPE_TYPE.getKey()).isEqualTo("ddd4j.datascope.type");
        assertThat(DataScopeSpan.ATTR_DATA_SCOPE_DECISION.getKey()).isEqualTo("ddd4j.datascope.decision");
    }
}