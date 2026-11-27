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

/**
 * 通过 <code>failure.method()</code>、<code>failure.path()</code>、<code>failure.cause()</code> 验证 <code>WebRequestFailure</code> 的运行行为。
 */
class WebRequestFailureTest {

    /**
     * 验证<code>failure.method()</code> 等于 <code>"POST"</code>，<code>failure.path()</code> 等于 <code>"/api/orders"</code>，<code>failure.getCause()</code> 与…为同一实例 <code>cause</code>。
     */
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
