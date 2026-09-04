package io.ddd4j.web.helidon;

import io.ddd4j.extension.otel.Ddd4jOtel;
import io.ddd4j.extension.otel.HttpSpan;
import io.ddd4j.extension.otel.WebOtelIntegration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ddd4j-web-helidon 模块可与 WebOtelIntegration 集成。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class Ddd4jHelidonOtelIntegrationTest {

    @Test
    void helidonModuleCanUseWebOtelIntegration() {
        Object span = WebOtelIntegration.startServerSpan("GET", "/api/test", new HashMap<>());
        assertThat(span).isNotNull();
    }

    @Test
    void helidonModuleCanUseWebOtelIntegration_withTraceparent() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        Object span = WebOtelIntegration.startServerSpan("POST", "/api/orders", headers);
        assertThat(span).isNotNull();
    }

    @Test
    void helidonModuleCanUseHttpSpanDirectly() {
        assertThat(HttpSpan.ATTR_HTTP_METHOD.getKey()).isEqualTo("http.request.method");
    }

    @Test
    void helidonModuleCanUseDdd4jOtel() {
        assertThat(Ddd4jOtel.TRACER_NAME).isEqualTo("io.ddd4j");
    }

    @Test
    void helidonModuleCanHandleAllHttpMethods() {
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE", "PATCH"}) {
            Object span = WebOtelIntegration.startServerSpan(method, "/api/test", new HashMap<>());
            assertThat(span).isNotNull();
        }
    }
}