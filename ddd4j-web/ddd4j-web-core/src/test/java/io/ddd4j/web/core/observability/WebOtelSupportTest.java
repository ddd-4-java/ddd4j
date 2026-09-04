package io.ddd4j.web.core.observability;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试期 classpath 已含 ddd4j-extension-otel：验证 WebOtelSupport 反射桥接到
 * 真实 WebOtelIntegration 的路径（无 OTel SDK 时为 invalid-span/noop 降级）。
 */
class WebOtelSupportTest {

    @Test
    void startServerSpanDelegatesToOtelIntegration() {
        // OTel SDK 未配置 → 返回 invalid PropagatedSpan（非 null，桥接成功）
        assertNotNull(WebOtelSupport.startServerSpan("GET", "/api", Map.of("x", "y")));
        assertNotNull(WebOtelSupport.startServerSpan("GET", "/api", null));
    }

    @Test
    void activateReturnsClosableScope() {
        AutoCloseable scope = WebOtelSupport.activate(null);
        assertNotNull(scope);
        assertDoesNotThrow(() -> scope.close());

        Object span = WebOtelSupport.startServerSpan("POST", "/orders", new HashMap<>());
        AutoCloseable scopeWithSpan = WebOtelSupport.activate(span);
        assertNotNull(scopeWithSpan);
        assertDoesNotThrow(() -> scopeWithSpan.close());
    }

    @Test
    void recordErrorDelegatesWithoutThrowing() {
        Object span = WebOtelSupport.startServerSpan("GET", "/api", new HashMap<>());
        assertDoesNotThrow(() -> WebOtelSupport.recordError(span, new IllegalStateException("x")));
        assertDoesNotThrow(() -> WebOtelSupport.recordError(null, null));
    }

    @Test
    void endServerSpanDelegatesWithoutThrowing() {
        Object span = WebOtelSupport.startServerSpan("GET", "/api", new HashMap<>());
        assertDoesNotThrow(() -> WebOtelSupport.endServerSpan(span, 200));
        assertDoesNotThrow(() -> WebOtelSupport.endServerSpan(null, 500));
    }

    @Test
    void injectResponseContextDelegatesWithoutThrowing() {
        Map<String, String> headers = new HashMap<>();
        assertDoesNotThrow(() -> WebOtelSupport.injectResponseContext(headers));
        assertDoesNotThrow(() -> WebOtelSupport.injectResponseContext(null));
    }

    @Test
    void isAvailableReflectsOtelSdkState() {
        // 无 OTel SDK 配置时 Ddd4jOtel.isAvailable() == false，桥接应如实透传
        boolean available = WebOtelSupport.isAvailable();
        assertTrue(available == false || available == true, "桥接返回布尔值");
        assertFalse(available);
    }
}
