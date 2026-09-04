package io.ddd4j.web.core.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WebRequestFailureTest {

    @Test
    void exposesComponents() {
        IllegalStateException cause = new IllegalStateException("boom");
        WebRequestFailure failure = new WebRequestFailure("POST", "/api/orders", cause);

        assertEquals("POST", failure.method());
        assertEquals("/api/orders", failure.path());
        assertSame(cause, failure.cause());
    }
}
