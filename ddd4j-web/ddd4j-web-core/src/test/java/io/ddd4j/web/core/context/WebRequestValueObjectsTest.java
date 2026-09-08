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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebRequestValueObjectsTest {

    @Test
    void webRequestDataExposesJavaBeanAccessors() {
        WebRequestData data = new WebRequestData("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/orders");

        assertEquals("request-1", data.getRequestId());
        assertEquals("trace-1", data.getTraceId());
        assertEquals("tenant-1", data.getTenantId());
        assertEquals("Bearer token", data.getAuthorization());
        assertEquals(Locale.CHINA, data.getLocale());
        assertEquals("203.0.113.1", data.getForwardedFor());
        assertEquals("203.0.113.2", data.getRealIp());
        assertEquals("10.0.0.1", data.getRemoteAddress());
        assertEquals("POST", data.getMethod());
        assertEquals("/orders", data.getPath());
    }

    @Test
    void webRequestContextExposesNormalizedJavaBeanAccessors() {
        WebRequestContext context = new WebRequestContext("request-1", null, "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "post", "/orders");

        assertEquals("request-1", context.getRequestId());
        assertEquals("request-1", context.getTraceId());
        assertEquals("tenant-1", context.getTenantId());
        assertEquals("Bearer token", context.getAuthorization());
        assertEquals(Locale.CHINA, context.getLocale());
        assertEquals("203.0.113.1", context.getClientIp());
        assertEquals("POST", context.getMethod());
        assertEquals("/orders", context.getPath());
    }
}
