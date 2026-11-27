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

/** \u901a\u8fc7 <code>error.status()</code>\u3001<code>error.code()</code>\u3001<code>error.message()</code> \u9a8c\u8bc1 <code>WebError</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class WebErrorTest {

    /** \u9a8c\u8bc1<code>error.status()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>error.code()</code> \u7b49\u4e8e <code>"PARAM"</code>\uff0c<code>error.getData()</code> \u7b49\u4e8e <code>"detail"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>error.code()</code>\u4e3a\u7a7a\uff0c<code>error.data()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void acceptsNullCodeAndData() {
        WebError error = new WebError(500, null, "boom", null);
        assertNull(error.code());
        assertNull(error.data());
    }

    /** \u9a8c\u8bc1<code>error.code()</code> \u7b49\u4e8e <code>Integer.valueOf(1001)</code>\u3002 */
    @Test
    void acceptsSerializableCode() {
        WebError error = new WebError(409, Integer.valueOf(1001), "conflict", null);
        assertEquals(Integer.valueOf(1001), error.code());
    }

    /** \u9a8c\u8bc1<code>error.code()</code> \u7b49\u4e8e <code>code</code>\u3002 */
    @Test
    void bizRuntimeCodeIsSerializable() {
        Serializable code = new BizRuntimeException(422, "VALIDATION", "x").getCode();
        WebError error = new WebError(422, code, "x", null);
        assertEquals(code, error.code());
    }

    /** \u9a8c\u8bc1<code>response.getCode()</code> \u7b49\u4e8e <code>"VALIDATION"</code>\uff0c<code>response.getMsg()</code> \u7b49\u4e8e <code>"bad input"</code>\uff0c<code>response.isOk()</code> \u7b49\u4e8e <code>Boolean.FALSE</code>\u3002 */
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
