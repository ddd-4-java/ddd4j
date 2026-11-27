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
        assertEquals("POST", failure.getMethod());
        assertEquals("/api/orders", failure.getPath());
        assertSame(cause, failure.getCause());
    }
}
