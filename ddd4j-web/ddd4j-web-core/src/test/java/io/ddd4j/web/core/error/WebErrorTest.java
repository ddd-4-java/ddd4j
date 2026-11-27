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

/**
 * 通过 <code>error.status()</code>、<code>error.code()</code>、<code>error.message()</code> 验证 <code>WebError</code> 的运行行为。
 */
class WebErrorTest {

    /**
     * 验证<code>error.status()</code> 等于 <code>400</code>，<code>error.code()</code> 等于 <code>"PARAM"</code>，<code>error.getData()</code> 等于 <code>"detail"</code>。
     */
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

    /**
     * 验证<code>error.code()</code>为空，<code>error.data()</code>为空。
     */
    @Test
    void acceptsNullCodeAndData() {
        WebError error = new WebError(500, null, "boom", null);
        assertNull(error.code());
        assertNull(error.data());
    }

    /**
     * 验证<code>error.code()</code> 等于 <code>Integer.valueOf(1001)</code>。
     */
    @Test
    void acceptsSerializableCode() {
        WebError error = new WebError(409, Integer.valueOf(1001), "conflict", null);
        assertEquals(Integer.valueOf(1001), error.code());
    }

    /**
     * 验证<code>error.code()</code> 等于 <code>code</code>。
     */
    @Test
    void bizRuntimeCodeIsSerializable() {
        Serializable code = new BizRuntimeException(422, "VALIDATION", "x").getCode();
        WebError error = new WebError(422, code, "x", null);
        assertEquals(code, error.code());
    }

    /**
     * 验证<code>response.getCode()</code> 等于 <code>"VALIDATION"</code>，<code>response.getMsg()</code> 等于 <code>"bad input"</code>，<code>response.isOk()</code> 等于 <code>Boolean.FALSE</code>。
     */
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
