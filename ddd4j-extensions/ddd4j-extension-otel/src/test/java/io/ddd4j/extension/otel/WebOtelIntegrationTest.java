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

    /** \u9a8c\u8bc1<code>span</code>\u975e\u7a7a\u3002 */
    @Test
    void startServerSpan_shouldReturnSpan() {
        Span span = WebOtelIntegration.startServerSpan("GET", "/api/users", new HashMap<>());
        assertThat(span).isNotNull();
    }

    /** \u9a8c\u8bc1<code>span</code>\u975e\u7a7a\u3002 */
    @Test
    void startServerSpan_withNullHeaders_shouldReturnSpan() {
        Span span = WebOtelIntegration.startServerSpan("POST", "/api/orders", null);
        assertThat(span).isNotNull();
    }

    /** \u9a8c\u8bc1<code>span</code>\u975e\u7a7a\u3002 */
    @Test
    void startServerSpan_withTraceparent_shouldNotThrow() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        Span span = WebOtelIntegration.startServerSpan("GET", "/api/users", headers);
        assertThat(span).isNotNull();
    }

    /** \u9a8c\u8bc1<code>scope</code>\u975e\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void recordError_withNull_shouldNotThrow() {
        WebOtelIntegration.recordError(null, null);
        assertThat(true).isTrue();
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void endServerSpan_shouldNotThrowWithNull() {
        WebOtelIntegration.endServerSpan(null, 500);
        assertThat(true).isTrue();
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void injectResponseContext_shouldNotThrowWithNull() {
        WebOtelIntegration.injectResponseContext(null);
        assertThat(true).isTrue();
    }

    /** \u9a8c\u8bc1<code>headers</code>\u975e\u7a7a\u3002 */
    @Test
    void injectResponseContext_withEmptyHeaders_shouldNotThrow() {
        Map<String, String> headers = new HashMap<>();
        WebOtelIntegration.injectResponseContext(headers);
        assertThat(headers).isNotNull();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void asHeaders_withNull_shouldReturnEmptyMap() {
        Map<String, String> result = WebOtelIntegration.asHeaders(null);
        assertThat(result).isEmpty();
    }

    /** \u9a8c\u8bc1<code>result</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"X-Foo"</code>\u3001<code>"bar"</code>\u3002 */
    @Test
    void asHeaders_withMap_shouldReturnCopy() {
        Map<String, String> source = new HashMap<>();
        source.put("X-Foo", "bar");
        Map<String, String> result = WebOtelIntegration.asHeaders(source);
        assertThat(result).containsEntry("X-Foo", "bar");
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>span</code>\u975e\u7a7a\u3002 */
    @Test
    void supportsAllHttpMethods() {
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}) {
            Span span = WebOtelIntegration.startServerSpan(method, "/path", new HashMap<>());
            assertThat(span).isNotNull();
        }
    }
}