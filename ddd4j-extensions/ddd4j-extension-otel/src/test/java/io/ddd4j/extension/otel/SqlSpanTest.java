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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SqlSpan} SQL span 测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class SqlSpanTest {

    /**
     * \u901a\u8fc7 <code>sb.toString()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param s \u4f20\u7ed9 <code>s.length()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param times \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>int</code> \u53c2\u6570 <code>times</code>
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>sb.toString()</code>
     */
    private static String replicate(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

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
        String longSql = "SELECT * FROM huge_table WHERE " + replicate("a = 1 AND ", 100) + "b = 2";
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
