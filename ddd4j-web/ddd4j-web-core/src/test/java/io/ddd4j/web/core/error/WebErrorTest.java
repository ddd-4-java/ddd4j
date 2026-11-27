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
        assertEquals(400, error.getStatus());
        assertEquals("PARAM", error.getCode());
        assertEquals("bad request", error.getMessage());
        assertEquals("detail", error.getData());
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
