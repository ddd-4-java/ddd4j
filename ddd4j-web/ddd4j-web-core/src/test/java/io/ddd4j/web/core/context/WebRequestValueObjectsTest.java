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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class WebRequestValueObjectsTest {

    /** 验证<code>same</code> 等于 <code>first</code>，<code>same.hashCode()</code> 等于 <code>first.hashCode()</code>，<code>otherPath</code> 不等于 <code>first</code>。 */
    @Test
    void webRequestDataValueEqualityIncludesTenantAndRequestPath() {
        WebRequestData first = new WebRequestData("request-1", "trace-1", "tenant-a", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/orders");
        WebRequestData same = new WebRequestData("request-1", "trace-1", "tenant-a", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/orders");
        WebRequestData otherTenant = new WebRequestData("request-1", "trace-1", "tenant-b", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/orders");
        WebRequestData otherPath = new WebRequestData("request-1", "trace-1", "tenant-a", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/goods");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, otherTenant);
        assertNotEquals(first, otherPath);
    }

    /** 验证<code>data.requestId()</code> 等于 <code>"request-1"</code>，<code>data.traceId()</code> 等于 <code>"trace-1"</code>，<code>data.path()</code> 等于 <code>"/orders"</code>。 */
    @Test
    void webRequestDataExposesJavaBeanAccessors() {
        WebRequestData data = new WebRequestData("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/orders");

        assertEquals("request-1", data.requestId());
        assertEquals("trace-1", data.traceId());
        assertEquals("tenant-1", data.tenantId());
        assertEquals("Bearer token", data.authorization());
        assertEquals(Locale.CHINA, data.locale());
        assertEquals("203.0.113.1", data.forwardedFor());
        assertEquals("203.0.113.2", data.realIp());
        assertEquals("10.0.0.1", data.remoteAddress());
        assertEquals("POST", data.method());
        assertEquals("/orders", data.path());
    }

    /** 验证<code>context.requestId()</code> 等于 <code>"request-1"</code>，<code>context.traceId()</code> 等于 <code>"request-1"</code>，<code>context.path()</code> 等于 <code>"/orders"</code>。 */
    @Test
    void webRequestContextExposesNormalizedJavaBeanAccessors() {
        WebRequestContext context = new WebRequestContext("request-1", null, "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "post", "/orders");

        assertEquals("request-1", context.requestId());
        assertEquals("request-1", context.traceId());
        assertEquals("tenant-1", context.tenantId());
        assertEquals("Bearer token", context.authorization());
        assertEquals(Locale.CHINA, context.locale());
        assertEquals("203.0.113.1", context.clientIp());
        assertEquals("POST", context.method());
        assertEquals("/orders", context.path());
    }
}
