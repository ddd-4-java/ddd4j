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

import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.web.core.version.ApiVersion;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WebRequestContext} 的 API 版本纯增量扩展测试。
 *
 * <p>验证旧八参构造器兼容（版本为 null、旧调用方零感知）、九参构造器携带版本、
 * equals/hashCode/toString 纳入新字段，以及 {@link WebRequestContextFactory} 的重载。
 */
class WebRequestContextApiVersionTest {

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token", Locale.CHINA, "203.0.113.1", "get", "/orders")</code>
     */
    private WebRequestContext legacyContext() {
        return new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders");
    }

    /** \u9a8c\u8bc1<code>context.apiVersion()</code>\u4e3a\u7a7a\uff0c<code>context.getApiVersion()</code>\u4e3a\u7a7a\uff0c<code>context.getPath()</code> \u7b49\u4e8e <code>"/orders"</code>\u3002 */
    @Test
    void eightArgumentConstructorKeepsApiVersionNull() {
        WebRequestContext context = legacyContext();

        assertNull(context.apiVersion());
        assertNull(context.getApiVersion());
        //旧字段归一化行为保持不变。
        assertEquals("request-1", context.getRequestId());
        assertEquals("trace-1", context.getTraceId());
        assertEquals("GET", context.getMethod());
        assertEquals("/orders", context.getPath());
    }

    /** \u9a8c\u8bc1<code>context.apiVersion()</code> \u7b49\u4e8e <code>ApiVersion.parse("2.1.0")</code>\uff0c<code>context.getApiVersion()</code> \u7b49\u4e8e <code>ApiVersion.parse("2.1.0")</code>\uff0c<code>context.apiVersion()</code> \u7b49\u4e8e <code>version</code>\u3002 */
    @Test
    void nineArgumentConstructorCarriesApiVersion() {
        ApiVersion version = ApiVersion.parse("2.1.0");
        WebRequestContext context = new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders", version);

        assertEquals(ApiVersion.parse("2.1.0"), context.apiVersion());
        assertEquals(ApiVersion.parse("2.1.0"), context.getApiVersion());
        assertEquals(version, context.apiVersion());
    }

    /** \u9a8c\u8bc1<code>explicitNull</code> \u7b49\u4e8e <code>legacy</code>\uff0c<code>explicitNull.hashCode()</code> \u7b49\u4e8e <code>legacy.hashCode()</code>\u3002 */
    @Test
    void eightAndNineArgumentConstructorsAgreeWhenVersionIsNull() {
        WebRequestContext legacy = legacyContext();
        WebRequestContext explicitNull = new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders", null);

        assertEquals(legacy, explicitNull);
        assertEquals(legacy.hashCode(), explicitNull.hashCode());
    }

    /** \u9a8c\u8bc1<code>sameVersion</code> \u7b49\u4e8e <code>withOldVersion</code>\uff0c<code>sameVersion.hashCode()</code> \u7b49\u4e8e <code>withOldVersion.hashCode()</code>\uff0c<code>noVersion.hashCode()</code> \u4e0d\u7b49\u4e8e <code>withOldVersion.hashCode()</code>\u3002 */
    @Test
    void equalsAndHashCodeIncludeApiVersion() {
        WebRequestContext withOldVersion = new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders", ApiVersion.parse("2.1.0"));
        WebRequestContext withNewVersion = new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders", ApiVersion.parse("3.0.0"));
        WebRequestContext sameVersion = new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders", ApiVersion.parse("2.1.0"));
        WebRequestContext noVersion = legacyContext();

        assertEquals(withOldVersion, sameVersion);
        assertEquals(withOldVersion.hashCode(), sameVersion.hashCode());
        assertNotEquals(withOldVersion, withNewVersion);
        assertNotEquals(withOldVersion, noVersion);
        assertNotEquals(withOldVersion.hashCode(), noVersion.hashCode());
    }

    /** \u9a8c\u8bc1<code>context.toString().contains("apiVersion=2.1.0")</code>\u4e3a\u771f\uff0c<code>legacyContext().toString().contains("apiVersion=null")</code>\u4e3a\u771f\u3002 */
    @Test
    void toStringIncludesApiVersion() {
        WebRequestContext context = new WebRequestContext("request-1", null, null, null,
                Locale.CHINA, null, "get", "/orders", ApiVersion.parse("2.1.0"));

        assertTrue(context.toString().contains("apiVersion=2.1.0"));
        assertTrue(legacyContext().toString().contains("apiVersion=null"));
    }

    /** \u9a8c\u8bc1<code>restored</code> \u7b49\u4e8e <code>context</code>\uff0c<code>restored.apiVersion()</code> \u7b49\u4e8e <code>ApiVersion.parse("2.1.0")</code>\u3002 */
    @Test
    void jsonRoundTripPreservesApiVersion() {
        WebRequestContext context = new WebRequestContext("request-1", "trace-1", "tenant-1", null,
                Locale.CHINA, "203.0.113.1", "get", "/orders", ApiVersion.parse("2.1.0"));

        String json = JsonKit.toJson(context);
        assertTrue(json.contains("\"apiVersion\":\"2.1.0\""));
        WebRequestContext restored = JsonKit.toObject(json, WebRequestContext.class);

        assertEquals(context, restored);
        assertEquals(ApiVersion.parse("2.1.0"), restored.apiVersion());
    }

    /** \u9a8c\u8bc1<code>withoutVersion.getApiVersion()</code>\u4e3a\u7a7a\uff0c<code>withNullVersion</code> \u7b49\u4e8e <code>withoutVersion</code>\uff0c<code>withVersion.getMethod()</code> \u7b49\u4e8e <code>"POST"</code>\u3002 */
    @Test
    void factoryCreateOverloadsPropagateApiVersion() {
        WebRequestContextFactory factory = new WebRequestContextFactory();
        WebRequestData data = new WebRequestData("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "203.0.113.2", "10.0.0.1", "POST", "/orders");

        WebRequestContext withoutVersion = factory.create(data);
        WebRequestContext withNullVersion = factory.create(data, null);
        WebRequestContext withVersion = factory.create(data, ApiVersion.parse("2.1.0"));

        assertNull(withoutVersion.getApiVersion());
        assertEquals(withoutVersion, withNullVersion);
        assertEquals(ApiVersion.parse("2.1.0"), withVersion.getApiVersion());
        assertEquals("request-1", withVersion.getRequestId());
        assertEquals("POST", withVersion.getMethod());
    }
}
