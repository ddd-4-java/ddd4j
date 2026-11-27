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

import io.github.easy4j.validation.provider.DataScopeProvider;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DataScopeSpan} 数据作用域评估 span 测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class DataScopeSpanTest {

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

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u771f\u3002 */
    @Test
    void evaluate_shouldReturnProviderResult() {
        DataScopeProvider provider = (dataType, data) -> true;
        boolean result = DataScopeSpan.evaluate("User", "user-123", provider);
        assertThat(result).isTrue();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u5047\u3002 */
    @Test
    void evaluate_denyCase_shouldReturnFalse() {
        DataScopeProvider provider = (dataType, data) -> false;
        boolean result = DataScopeSpan.evaluate("Order", "ord-001", provider);
        assertThat(result).isFalse();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u771f\uff0c<code>nullResult</code>\u4e3a\u5047\u3002 */
    @Test
    void evaluate_shouldRespectApplicationPolicyForNullData() {
        DataScopeProvider provider = (type, data) -> Objects.nonNull(data);
        boolean result = DataScopeSpan.evaluate("Test", "valid-data", provider);
        assertThat(result).isTrue();

        boolean nullResult = DataScopeSpan.evaluate("Test", null, provider);
        assertThat(nullResult).isFalse();
    }

    /** \u9a8c\u8bc1<code>called</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u771f\uff0c<code>called</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>DataScopeSpan.evaluate("Type", "value", throwing)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void evaluate_propagateException() {
        DataScopeProvider throwing = (dataType, data) -> {
            throw new IllegalStateException("scope evaluation failed");
        };

        assertThatThrownBy(() -> DataScopeSpan.evaluate("Type", "value", throwing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("scope evaluation failed");
    }

    /** \u9a8c\u8bc1<code>DataScopeSpan.ATTR_DATA_SCOPE_TYPE.getKey()</code> \u7b49\u4e8e <code>"ddd4j.datascope.type"</code>\uff0c<code>DataScopeSpan.ATTR_DATA_SCOPE_DECISION.getKey()</code> \u7b49\u4e8e <code>"ddd4j.datascope.decision"</code>\u3002 */
    @Test
    void evaluate_attributeKeys_shouldHaveCorrectNames() {
        assertThat(DataScopeSpan.ATTR_DATA_SCOPE_TYPE.getKey()).isEqualTo("ddd4j.datascope.type");
        assertThat(DataScopeSpan.ATTR_DATA_SCOPE_DECISION.getKey()).isEqualTo("ddd4j.datascope.decision");
    }
}
