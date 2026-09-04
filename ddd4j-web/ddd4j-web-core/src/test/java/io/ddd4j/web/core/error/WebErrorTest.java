package io.ddd4j.web.core.error;

import io.ddd4j.core.exception.BizRuntimeException;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebErrorTest {

    @Test
    void exposesComponents() {
        WebError error = new WebError(400, "PARAM", "bad request", "detail");

        assertEquals(400, error.status());
        assertEquals("PARAM", error.code());
        assertEquals("bad request", error.message());
        assertEquals("detail", error.data());
    }

    @Test
    void acceptsNullCodeAndData() {
        WebError error = new WebError(500, null, "boom", null);
        assertNull(error.code());
        assertNull(error.data());
    }

    @Test
    void acceptsSerializableCode() {
        WebError error = new WebError(409, Integer.valueOf(1001), "conflict", null);
        assertEquals(Integer.valueOf(1001), error.code());
    }

    @Test
    void bizRuntimeCodeIsSerializable() {
        Serializable code = new BizRuntimeException(422, "VALIDATION", "x").getCode();
        WebError error = new WebError(422, code, "x", null);
        assertEquals(code, error.code());
    }

    @Test
    void toResponseCarriesErrorComponents() {
        WebError error = new WebError(422, "VALIDATION", "bad input", "detail");

        io.ddd4j.core.api.R<Object> response = error.toResponse();

        assertEquals("VALIDATION", response.getCode());
        assertEquals("bad input", response.getMsg());
        assertEquals("detail", response.getData());
        assertEquals(Boolean.FALSE, response.isOk());
    }
}
