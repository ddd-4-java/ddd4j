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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 三线共同的请求上下文 JSON 往返契约。
 */
class WebRequestContextJsonContractTest {

    /**
     * 验证请求上下文在 JSON 往返后保留请求标识、规范化方法和租户。
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void roundTripKeepsNormalizedRequestFields() throws Exception {
        WebRequestContext context = new WebRequestContext(
                "request-1", null, "tenant-1", "Bearer token", Locale.CHINA,
                "203.0.113.1", "get", "/orders");
        ObjectMapper mapper = new ObjectMapper();

        WebRequestContext restored = mapper.readValue(
                mapper.writeValueAsString(context), WebRequestContext.class);

        assertEquals(context, restored);
        assertEquals("request-1", restored.traceId());
        assertEquals("GET", restored.method());
    }
}
