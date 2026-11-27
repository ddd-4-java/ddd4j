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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DataScopeSpan} 数据作用域评估 span 测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class DataScopeSpanTest {

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
     * 验证<code>result</code>为真。
     */
    @Test
    void evaluate_shouldReturnProviderResult() {
        DataScopeProvider provider = (dataType, data) -> true;
        boolean result = DataScopeSpan.evaluate("User", "user-123", provider);
        assertThat(result).isTrue();
    }

    /**
     * 验证<code>result</code>为假。
     */
    @Test
    void evaluate_denyCase_shouldReturnFalse() {
        DataScopeProvider provider = (dataType, data) -> false;
        boolean result = DataScopeSpan.evaluate("Order", "ord-001", provider);
        assertThat(result).isFalse();
    }

    /**
     * 验证<code>result</code>为真，<code>nullResult</code>为假。
     */
    @Test
    void evaluate_shouldRespectApplicationPolicyForNullData() {
        DataScopeProvider provider = (type, data) -> Objects.nonNull(data);
        boolean result = DataScopeSpan.evaluate("Test", "valid-data", provider);
        assertThat(result).isTrue();

        boolean nullResult = DataScopeSpan.evaluate("Test", null, provider);
        assertThat(nullResult).isFalse();
    }

    /**
     * 验证<code>called</code>为真。
     */
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

    /**
     * 验证<code>result</code>为真，<code>called</code>为真。
     */
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

    /**
     * 验证<code>DataScopeSpan.evaluate("Type", "value", throwing)</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void evaluate_propagateException() {
        DataScopeProvider throwing = (dataType, data) -> {
            throw new IllegalStateException("scope evaluation failed");
        };

        assertThatThrownBy(() -> DataScopeSpan.evaluate("Type", "value", throwing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("scope evaluation failed");
    }

    /**
     * 验证<code>DataScopeSpan.ATTR_DATA_SCOPE_TYPE.getKey()</code> 等于 <code>"ddd4j.datascope.type"</code>，<code>DataScopeSpan.ATTR_DATA_SCOPE_DECISION.getKey()</code> 等于 <code>"ddd4j.datascope.decision"</code>。
     */
    @Test
    void evaluate_attributeKeys_shouldHaveCorrectNames() {
        assertThat(DataScopeSpan.ATTR_DATA_SCOPE_TYPE.getKey()).isEqualTo("ddd4j.datascope.type");
        assertThat(DataScopeSpan.ATTR_DATA_SCOPE_DECISION.getKey()).isEqualTo("ddd4j.datascope.decision");
    }
}
