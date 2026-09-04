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
 * {@link HttpSpan} HTTP SERVER span 工具测试（无 SDK 依赖，纯行为验证）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class HttpSpanTest {

    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    @Test
    void serverSpan_shouldReturnInvalidWhenOtelNotAvailable() {
        Span span = HttpSpan.serverSpan("GET", "/api/users", io.opentelemetry.context.Context.current());
        assertThat(span).isNotNull();
        assertThat(span.getSpanContext().isValid()).isFalse();
    }

    @Test
    void extractContext_withNullHeaders_shouldReturnCurrentContext() {
        io.opentelemetry.context.Context ctx = HttpSpan.extractContext(null);
        assertThat(ctx).isNotNull();
    }

    @Test
    void extractContext_withEmptyHeaders_shouldReturnCurrentContext() {
        Map<String, String> headers = new HashMap<>();
        io.opentelemetry.context.Context ctx = HttpSpan.extractContext(headers);
        assertThat(ctx).isNotNull();
    }

    @Test
    void extractContext_withTraceparent_shouldNotThrow() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        io.opentelemetry.context.Context ctx = HttpSpan.extractContext(headers);
        assertThat(ctx).isNotNull();
    }

    @Test
    void endServerSpan_withNullSpan_shouldNotThrow() {
        HttpSpan.endServerSpan(null, 200);
        HttpSpan.endServerSpan(null, 500);
        assertThat(true).isTrue();
    }

    @Test
    void endServerSpan_withInvalidSpan_shouldNotThrow() {
        HttpSpan.endServerSpan(Span.getInvalid(), 404);
        assertThat(true).isTrue();
    }

    @Test
    void recordError_withNullSpan_shouldNotThrow() {
        HttpSpan.recordError(null, new RuntimeException("test"));
        assertThat(true).isTrue();
    }

    @Test
    void recordError_withNullThrowable_shouldNotThrow() {
        HttpSpan.recordError(Span.getInvalid(), null);
        assertThat(true).isTrue();
    }

    @Test
    void injectContext_withNullHeaders_shouldNotThrow() {
        HttpSpan.injectContext(null);
        assertThat(true).isTrue();
    }

    @Test
    void injectContext_withEmptyHeaders_shouldNotThrow() {
        Map<String, String> headers = new HashMap<>();
        HttpSpan.injectContext(headers);
        assertThat(headers).isNotNull();
    }

    @Test
    void makeCurrent_withInvalidSpan_shouldReturnNoopScope() {
        io.opentelemetry.context.Scope scope = HttpSpan.makeCurrent(Span.getInvalid());
        assertThat(scope).isNotNull();
        scope.close();
    }

    @Test
    void makeCurrent_withNullSpan_shouldReturnNoopScope() {
        io.opentelemetry.context.Scope scope = HttpSpan.makeCurrent(null);
        assertThat(scope).isNotNull();
        scope.close();
    }

    @Test
    void attributeKeys_shouldHaveCorrectNames() {
        assertThat(HttpSpan.ATTR_HTTP_METHOD.getKey()).isEqualTo("http.request.method");
        assertThat(HttpSpan.ATTR_HTTP_ROUTE.getKey()).isEqualTo("http.route");
        assertThat(HttpSpan.ATTR_HTTP_STATUS.getKey()).isEqualTo("http.response.status_code");
        assertThat(HttpSpan.ATTR_URL_PATH.getKey()).isEqualTo("url.path");
        assertThat(HttpSpan.ATTR_CLIENT_IP.getKey()).isEqualTo("client.address");
        assertThat(HttpSpan.ATTR_DDD4J_REQUEST_ID.getKey()).isEqualTo("ddd4j.request.id");
        assertThat(HttpSpan.ATTR_DDD4J_TENANT_ID.getKey()).isEqualTo("ddd4j.tenant.id");
    }
}