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

    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @Test
    void startServerSpan_shouldReturnSpan() {
        Span span = WebOtelIntegration.startServerSpan("GET", "/api/users", new HashMap<>());
        assertThat(span).isNotNull();
    }

    @Test
    void startServerSpan_withNullHeaders_shouldReturnSpan() {
        Span span = WebOtelIntegration.startServerSpan("POST", "/api/orders", null);
        assertThat(span).isNotNull();
    }

    @Test
    void startServerSpan_withTraceparent_shouldNotThrow() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        Span span = WebOtelIntegration.startServerSpan("GET", "/api/users", headers);
        assertThat(span).isNotNull();
    }

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

    @Test
    void recordError_withNull_shouldNotThrow() {
        WebOtelIntegration.recordError(null, null);
        assertThat(true).isTrue();
    }

    @Test
    void endServerSpan_shouldNotThrowWithNull() {
        WebOtelIntegration.endServerSpan(null, 500);
        assertThat(true).isTrue();
    }

    @Test
    void injectResponseContext_shouldNotThrowWithNull() {
        WebOtelIntegration.injectResponseContext(null);
        assertThat(true).isTrue();
    }

    @Test
    void injectResponseContext_withEmptyHeaders_shouldNotThrow() {
        Map<String, String> headers = new HashMap<>();
        WebOtelIntegration.injectResponseContext(headers);
        assertThat(headers).isNotNull();
    }

    @Test
    void asHeaders_withNull_shouldReturnEmptyMap() {
        Map<String, String> result = WebOtelIntegration.asHeaders(null);
        assertThat(result).isEmpty();
    }

    @Test
    void asHeaders_withMap_shouldReturnCopy() {
        Map<String, String> source = new HashMap<>();
        source.put("X-Foo", "bar");
        Map<String, String> result = WebOtelIntegration.asHeaders(source);
        assertThat(result).containsEntry("X-Foo", "bar");
    }

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

    @Test
    void supportsAllHttpMethods() {
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}) {
            Span span = WebOtelIntegration.startServerSpan(method, "/path", new HashMap<>());
            assertThat(span).isNotNull();
        }
    }
}