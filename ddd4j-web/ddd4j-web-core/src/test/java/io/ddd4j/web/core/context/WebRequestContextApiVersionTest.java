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

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WebRequestContext} 的 API 版本纯增量扩展测试。
 *
 * <p>验证旧八参构造器兼容（版本为 null、旧调用方零感知）、九参构造器携带版本、
 * equals/hashCode/toString 纳入新字段，以及 {@link WebRequestContextFactory} 的重载。
 */
class WebRequestContextApiVersionTest {

    /**
     * 创建 <code>new WebRequestContext</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token", Locale.CHINA, "203.0.113.1", "get", "/orders")</code>
     */
    private WebRequestContext legacyContext() {
        return new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders");
    }

    /**
     * 验证<code>context.apiVersion()</code>为空，<code>context.getApiVersion()</code>为空，<code>context.getPath()</code> 等于 <code>"/orders"</code>。
     */
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

    /**
     * 验证<code>context.apiVersion()</code> 等于 <code>ApiVersion.parse("2.1.0")</code>，<code>context.getApiVersion()</code> 等于 <code>ApiVersion.parse("2.1.0")</code>，<code>context.apiVersion()</code> 等于 <code>version</code>。
     */
    @Test
    void nineArgumentConstructorCarriesApiVersion() {
        ApiVersion version = ApiVersion.parse("2.1.0");
        WebRequestContext context = new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders", version);

        assertEquals(ApiVersion.parse("2.1.0"), context.apiVersion());
        assertEquals(ApiVersion.parse("2.1.0"), context.getApiVersion());
        assertEquals(version, context.apiVersion());
    }

    /**
     * 验证<code>explicitNull</code> 等于 <code>legacy</code>，<code>explicitNull.hashCode()</code> 等于 <code>legacy.hashCode()</code>。
     */
    @Test
    void eightAndNineArgumentConstructorsAgreeWhenVersionIsNull() {
        WebRequestContext legacy = legacyContext();
        WebRequestContext explicitNull = new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders", null);

        assertEquals(legacy, explicitNull);
        assertEquals(legacy.hashCode(), explicitNull.hashCode());
    }

    /**
     * 验证<code>sameVersion</code> 等于 <code>withOldVersion</code>，<code>sameVersion.hashCode()</code> 等于 <code>withOldVersion.hashCode()</code>，<code>noVersion.hashCode()</code> 不等于 <code>withOldVersion.hashCode()</code>。
     */
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

    /**
     * 验证<code>context.toString().contains("apiVersion=2.1.0")</code>为真，<code>legacyContext().toString().contains("apiVersion=null")</code>为真。
     */
    @Test
    void toStringIncludesApiVersion() {
        WebRequestContext context = new WebRequestContext("request-1", null, null, null,
                Locale.CHINA, null, "get", "/orders", ApiVersion.parse("2.1.0"));

        assertTrue(context.toString().contains("apiVersion=2.1.0"));
        assertTrue(legacyContext().toString().contains("apiVersion=null"));
    }

    /**
     * 验证<code>restored</code> 等于 <code>context</code>，<code>restored.apiVersion()</code> 等于 <code>ApiVersion.parse("2.1.0")</code>。
     */
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

    /**
     * 验证<code>withoutVersion.getApiVersion()</code>为空，<code>withNullVersion</code> 等于 <code>withoutVersion</code>，<code>withVersion.getMethod()</code> 等于 <code>"POST"</code>。
     */
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
