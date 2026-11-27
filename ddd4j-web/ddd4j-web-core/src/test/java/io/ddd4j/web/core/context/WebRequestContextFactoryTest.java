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

    /**
     * 可重复查找同一测试记录的固定标识。
     */
    private static final RequestIdGenerator FIXED_ID = () -> "generated-request";

    /**
     * 创建 <code>new WebRequestData</code> 供当前测试路径调用。
     *
     * @param requestId 用于检查请求头及响应对应关系的标识
     * @return 当前测试使用的结果，取自 <code>new WebRequestData(requestId, "t-1", "tenant-a", "Bearer x", Locale.CHINA, "1.2.3.4", null, "10.0.0.1", "GET", "/api")</code>
     */
    private static WebRequestData requestData(String requestId) {
        return new WebRequestData(requestId, "t-1", "tenant-a", "Bearer x", Locale.CHINA,
                "1.2.3.4", null, "10.0.0.1", "GET", "/api");
    }

    /**
     * 验证<code>context.requestId()</code> 等于 <code>"provided-id"</code>，<code>context.clientIp()</code> 等于 <code>"10.0.0.1"</code>。
     */
    @Test
    void createUsesRequestIdWhenProvided() {
        WebRequestContextFactory factory = new WebRequestContextFactory(FIXED_ID,
                (f, r, a) -> "10.0.0.1");
        WebRequestContext context = factory.create(requestData("provided-id"));

        assertEquals("provided-id", context.requestId());
        assertEquals("10.0.0.1", context.clientIp());
    }

    /**
     * 验证<code>context.requestId()</code> 等于 <code>"generated-request"</code>。
     */
    @Test
    void createGeneratesRequestIdWhenBlank() {
        WebRequestContextFactory factory = new WebRequestContextFactory(FIXED_ID,
                (f, r, a) -> null);
        WebRequestContext context = factory.create(requestData("  "));

        assertEquals("generated-request", context.requestId());
    }

    /**
     * 验证<code>factory.create(requestData(null))</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void createRejectsBlankGeneratedRequestId() {
        WebRequestContextFactory factory = new WebRequestContextFactory(() -> "  ",
                (f, r, a) -> null);
        assertThrows(IllegalStateException.class, () -> factory.create(requestData(null)));
    }

    /**
     * 验证<code>factory.create(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void createRejectsNullData() {
        WebRequestContextFactory factory = new WebRequestContextFactory(FIXED_ID, (f, r, a) -> null);
        assertThrows(NullPointerException.class, () -> factory.create(null));
    }

    /**
     * 验证<code>context.requestId().trim().isEmpty()</code>为假。
     */
    @Test
    void defaultFactoryUsesDefaults() {
        WebRequestContextFactory factory = new WebRequestContextFactory();
        WebRequestContext context = factory.create(requestData(null));
        assertFalse(context.requestId().trim().isEmpty());
    }
}
