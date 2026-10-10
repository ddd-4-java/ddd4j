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

    private WebRequestContext legacyContext() {
        return new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders");
    }

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

    @Test
    void nineArgumentConstructorCarriesApiVersion() {
        ApiVersion version = ApiVersion.parse("2.1.0");
        WebRequestContext context = new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders", version);

        assertEquals(ApiVersion.parse("2.1.0"), context.apiVersion());
        assertEquals(ApiVersion.parse("2.1.0"), context.getApiVersion());
        assertEquals(version, context.apiVersion());
    }

    @Test
    void eightAndNineArgumentConstructorsAgreeWhenVersionIsNull() {
        WebRequestContext legacy = legacyContext();
        WebRequestContext explicitNull = new WebRequestContext("request-1", "trace-1", "tenant-1", "Bearer token",
                Locale.CHINA, "203.0.113.1", "get", "/orders", null);

        assertEquals(legacy, explicitNull);
        assertEquals(legacy.hashCode(), explicitNull.hashCode());
    }

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

    @Test
    void toStringIncludesApiVersion() {
        WebRequestContext context = new WebRequestContext("request-1", null, null, null,
                Locale.CHINA, null, "get", "/orders", ApiVersion.parse("2.1.0"));

        assertTrue(context.toString().contains("apiVersion=2.1.0"));
        assertTrue(legacyContext().toString().contains("apiVersion=null"));
    }

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
