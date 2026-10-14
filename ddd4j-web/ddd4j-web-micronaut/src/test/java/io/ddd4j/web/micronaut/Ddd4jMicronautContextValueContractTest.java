package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.context.WebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线 Micronaut 请求状态的读取与值方法契约。 */
class Ddd4jMicronautContextValueContractTest {

    @Test
    void beanAndComponentAccessorsReadSameState() {
        WebRequestContext request = request("request-1");
        Ddd4jMicronautContext context = new Ddd4jMicronautContext(request, Optional.empty());

        assertEquals(request, context.requestContext());
        assertEquals(request, context.getRequestContext());
        assertEquals(Optional.empty(), context.subject());
        assertEquals(Optional.empty(), context.getSubject());
    }

    @Test
    void valueMethodsUseRequestAndSubject() {
        Ddd4jMicronautContext left = new Ddd4jMicronautContext(request("request-1"), Optional.empty());
        Ddd4jMicronautContext same = new Ddd4jMicronautContext(request("request-1"), Optional.empty());
        Ddd4jMicronautContext different = new Ddd4jMicronautContext(request("request-2"), Optional.empty());

        assertEquals(left, same);
        assertEquals(left.hashCode(), same.hashCode());
        assertNotEquals(left, different);
        assertTrue(left.toString().contains("Ddd4jMicronautContext"));
    }

    private WebRequestContext request(String id) {
        return new WebRequestContext(id, "trace-1", "tenant-1", null,
                Locale.ENGLISH, "127.0.0.1", "GET", "/test");
    }
}
