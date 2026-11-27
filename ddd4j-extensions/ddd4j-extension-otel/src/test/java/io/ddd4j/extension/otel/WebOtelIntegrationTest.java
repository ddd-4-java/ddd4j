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
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WebOtelIntegration} 8 个 web 框架的统一集成点测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class WebOtelIntegrationTest {

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
     * 验证<code>span</code>非空。
     */
    @Test
    void startServerSpan_shouldReturnSpan() {
        Span span = WebOtelIntegration.startServerSpan("GET", "/api/users", new HashMap<>());
        assertThat(span).isNotNull();
    }

    /**
     * 验证<code>span</code>非空。
     */
    @Test
    void startServerSpan_withNullHeaders_shouldReturnSpan() {
        Span span = WebOtelIntegration.startServerSpan("POST", "/api/orders", null);
        assertThat(span).isNotNull();
    }

    /**
     * 验证<code>span</code>非空。
     */
    @Test
    void startServerSpan_withTraceparent_shouldNotThrow() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        Span span = WebOtelIntegration.startServerSpan("GET", "/api/users", headers);
        assertThat(span).isNotNull();
    }

    /**
     * 验证<code>scope</code>非空。
     */
    @Test
    void activate_shouldReturnNonNullScope() {
        Span span = WebOtelIntegration.startServerSpan("GET", "/api", new HashMap<>());
        io.opentelemetry.context.Scope scope = WebOtelIntegration.activate(span);
        try {
            assertThat(scope).isNotNull();
        } finally {
            scope.close();
        }
    }

    /**
     * 验证<code>true</code>为真。
     */
    @Test
    void recordError_withNull_shouldNotThrow() {
        WebOtelIntegration.recordError(null, null);
        assertThat(true).isTrue();
    }

    /**
     * 验证<code>true</code>为真。
     */
    @Test
    void endServerSpan_shouldNotThrowWithNull() {
        WebOtelIntegration.endServerSpan(null, 500);
        assertThat(true).isTrue();
    }

    /**
     * 验证<code>true</code>为真。
     */
    @Test
    void injectResponseContext_shouldNotThrowWithNull() {
        WebOtelIntegration.injectResponseContext(null);
        assertThat(true).isTrue();
    }

    /**
     * 验证<code>headers</code>非空。
     */
    @Test
    void injectResponseContext_withEmptyHeaders_shouldNotThrow() {
        Map<String, String> headers = new HashMap<>();
        WebOtelIntegration.injectResponseContext(headers);
        assertThat(headers).isNotNull();
    }

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void asHeaders_withNull_shouldReturnEmptyMap() {
        Map<String, String> result = WebOtelIntegration.asHeaders(null);
        assertThat(result).isEmpty();
    }

    /**
     * 验证<code>result</code> 含对应条目 <code>"X-Foo"</code>、<code>"bar"</code>。
     */
    @Test
    void asHeaders_withMap_shouldReturnCopy() {
        Map<String, String> source = new HashMap<>();
        source.put("X-Foo", "bar");
        Map<String, String> result = WebOtelIntegration.asHeaders(source);
        assertThat(result).containsEntry("X-Foo", "bar");
    }

    /**
     * 验证<code>true</code>为真。
     */
    @Test
    void fullLifecycle_shouldWorkWithoutException() {
        Map<String, String> reqHeaders = new HashMap<>();
        Span span = WebOtelIntegration.startServerSpan("PUT", "/api/users/{id}", reqHeaders);
        io.opentelemetry.context.Scope scope = WebOtelIntegration.activate(span);
        try {
            // 模拟业务逻辑
        } catch (Throwable t) {
            WebOtelIntegration.recordError(span, t);
        } finally {
            scope.close();
        }
        WebOtelIntegration.endServerSpan(span, 200);

        Map<String, String> respHeaders = new HashMap<>();
        WebOtelIntegration.injectResponseContext(respHeaders);

        assertThat(true).isTrue();
    }

    /**
     * 验证<code>true</code>为真。
     */
    @Test
    void fullLifecycle_withError_shouldNotThrow() {
        Span span = WebOtelIntegration.startServerSpan("POST", "/api/orders", new HashMap<>());
        io.opentelemetry.context.Scope scope = WebOtelIntegration.activate(span);
        try {
            WebOtelIntegration.recordError(span, new RuntimeException("simulated"));
        } finally {
            scope.close();
        }
        WebOtelIntegration.endServerSpan(span, 500);
        assertThat(true).isTrue();
    }

    /**
     * 验证<code>span</code>非空。
     */
    @Test
    void supportsAllHttpMethods() {
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}) {
            Span span = WebOtelIntegration.startServerSpan(method, "/path", new HashMap<>());
            assertThat(span).isNotNull();
        }
    }
}
