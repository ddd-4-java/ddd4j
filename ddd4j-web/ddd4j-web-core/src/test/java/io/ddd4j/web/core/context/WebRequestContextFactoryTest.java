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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求上下文构建与请求 ID 解析的兼容契约。
 */
class WebRequestContextFactoryTest {

    /** \u53ef\u91cd\u590d\u67e5\u627e\u540c\u4e00\u6d4b\u8bd5\u8bb0\u5f55\u7684\u56fa\u5b9a\u6807\u8bc6\u3002 */
    private static final RequestIdGenerator FIXED_ID = () -> "generated-request";

    /**
     * \u521b\u5efa <code>new WebRequestData</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param requestId \u7528\u4e8e\u68c0\u67e5\u8bf7\u6c42\u5934\u53ca\u54cd\u5e94\u5bf9\u5e94\u5173\u7cfb\u7684\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestData(requestId, "t-1", "tenant-a", "Bearer x", Locale.CHINA, "1.2.3.4", null, "10.0.0.1", "GET", "/api")</code>
     */
    private static WebRequestData requestData(String requestId) {
        return new WebRequestData(requestId, "t-1", "tenant-a", "Bearer x", Locale.CHINA,
                "1.2.3.4", null, "10.0.0.1", "GET", "/api");
    }

    /** \u9a8c\u8bc1<code>context.requestId()</code> \u7b49\u4e8e <code>"provided-id"</code>\uff0c<code>context.clientIp()</code> \u7b49\u4e8e <code>"10.0.0.1"</code>\u3002 */
    @Test
    void createUsesRequestIdWhenProvided() {
        WebRequestContextFactory factory = new WebRequestContextFactory(FIXED_ID,
                (f, r, a) -> "10.0.0.1");
        WebRequestContext context = factory.create(requestData("provided-id"));

        assertEquals("provided-id", context.requestId());
        assertEquals("10.0.0.1", context.clientIp());
    }

    /** \u9a8c\u8bc1<code>context.requestId()</code> \u7b49\u4e8e <code>"generated-request"</code>\u3002 */
    @Test
    void createGeneratesRequestIdWhenBlank() {
        WebRequestContextFactory factory = new WebRequestContextFactory(FIXED_ID,
                (f, r, a) -> null);
        WebRequestContext context = factory.create(requestData("  "));

        assertEquals("generated-request", context.requestId());
    }

    /** \u9a8c\u8bc1<code>factory.create(requestData(null))</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void createRejectsBlankGeneratedRequestId() {
        WebRequestContextFactory factory = new WebRequestContextFactory(() -> "  ",
                (f, r, a) -> null);
        assertThrows(IllegalStateException.class, () -> factory.create(requestData(null)));
    }

    /** \u9a8c\u8bc1<code>factory.create(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void createRejectsNullData() {
        WebRequestContextFactory factory = new WebRequestContextFactory(FIXED_ID, (f, r, a) -> null);
        assertThrows(NullPointerException.class, () -> factory.create(null));
    }

    /** \u9a8c\u8bc1<code>context.requestId().trim().isEmpty()</code>\u4e3a\u5047\u3002 */
    @Test
    void defaultFactoryUsesDefaults() {
        WebRequestContextFactory factory = new WebRequestContextFactory();
        WebRequestContext context = factory.create(requestData(null));
        assertFalse(context.requestId().trim().isEmpty());
    }
}
