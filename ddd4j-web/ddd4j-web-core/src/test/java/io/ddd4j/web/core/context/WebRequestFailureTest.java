package io.ddd4j.web.core.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** \u901a\u8fc7 <code>failure.method()</code>\u3001<code>failure.path()</code>\u3001<code>failure.cause()</code> \u9a8c\u8bc1 <code>WebRequestFailure</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class WebRequestFailureTest {

    /** \u9a8c\u8bc1<code>failure.method()</code> \u7b49\u4e8e <code>"POST"</code>\uff0c<code>failure.path()</code> \u7b49\u4e8e <code>"/api/orders"</code>\uff0c<code>failure.getCause()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>cause</code>\u3002 */
    @Test
    void exposesComponents() {
        IllegalStateException cause = new IllegalStateException("boom");
        WebRequestFailure failure = new WebRequestFailure("POST", "/api/orders", cause);

        assertEquals("POST", failure.method());
        assertEquals("/api/orders", failure.path());
        assertSame(cause, failure.cause());
    }
}
