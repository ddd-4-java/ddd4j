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
package io.ddd4j.web.core.version;

import io.ddd4j.web.core.context.WebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApiVersionResolver} 优先级与双模式测试。
 *
 * <p>对应规格 Scenario（API 版本解析）：请求头优先、路径版本段回退、双缺失回落默认版本、
 * 非法版本值回退默认版本、非法版本值拒绝。
 */
class ApiVersionResolverTest {

    private final ApiVersionResolver fallback =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);

    private final ApiVersionResolver strict =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT);

    @Test
    void requestHeaderTakesPriorityOverPathSegment() {
        //规格 Scenario：请求头优先；头与路径不一致时以头为准。
        ApiVersionResult result = fallback.resolve("2.1.0", "/v1/orders");

        assertEquals(ApiVersion.parse("2.1.0"), result.version());
        assertEquals(ApiVersionSource.HEADER, result.source());
        assertEquals("2.1.0", result.rawValue());
    }

    @Test
    void pathVersionSegmentIsUsedWhenHeaderAbsent() {
        //规格 Scenario：路径版本段回退。
        ApiVersionResult first = fallback.resolve(null, "/v2/orders");
        assertEquals(ApiVersion.parse("2.0.0"), first.version());
        assertEquals(ApiVersionSource.PATH, first.source());
        assertEquals("v2", first.rawValue());

        ApiVersionResult nested = fallback.resolve(null, "/api/v3.1/orders");
        assertEquals(ApiVersion.parse("3.1.0"), nested.version());
        assertEquals(ApiVersionSource.PATH, nested.source());
    }

    @Test
    void defaultVersionIsUsedWhenBothSourcesMissing() {
        //规格 Scenario：双缺失回落默认版本。
        ApiVersionResult fromPath = fallback.resolve(null, "/orders");
        assertEquals(ApiVersion.parse("1.0.0"), fromPath.version());
        assertEquals(ApiVersionSource.DEFAULT, fromPath.source());

        ApiVersionResult noPath = fallback.resolve("  ", null);
        assertEquals(ApiVersion.parse("1.0.0"), noPath.version());
        assertEquals(ApiVersionSource.DEFAULT, noPath.source());
    }

    @Test
    void illegalHeaderFallsBackToDefaultInFallbackMode() {
        //规格 Scenario：非法版本值回退默认版本（即使路径中存在版本段也回退默认）。
        ApiVersionResult result = fallback.resolve("2.x", "/v9/orders");

        assertEquals(ApiVersion.parse("1.0.0"), result.version());
        assertEquals(ApiVersionSource.DEFAULT, result.source());
    }

    @Test
    void illegalHeaderIsRejectedInStrictMode() {
        //规格 Scenario：非法版本值拒绝（错误信息指明非法值）。
        ApiVersionRejectedException exception = assertThrows(ApiVersionRejectedException.class,
                () -> strict.resolve("2.x", "/orders"));

        assertEquals(ApiVersionRejectedException.STATUS, exception.getStatus());
        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("2.x"));
    }

    @Test
    void nonShapedPathSegmentIsNotTreatedAsVersion() {
        ApiVersionResult result = fallback.resolve(null, "/orders/v2-detail");
        assertEquals(ApiVersionSource.DEFAULT, result.source());
        assertEquals(ApiVersion.parse("1.0.0"), result.version());
    }

    @Test
    void contextOverloadResolvesPathOnly() {
        WebRequestContext context = new WebRequestContext("request-1", null, null, null,
                Locale.CHINA, null, "GET", "/v2/orders");

        ApiVersionResult result = fallback.resolve(context);

        assertEquals(ApiVersion.parse("2.0.0"), result.version());
        assertEquals(ApiVersionSource.PATH, result.source());
    }

    @Test
    void exposesConfiguredDefaultVersionAndMode() {
        assertEquals(ApiVersion.parse("1.0.0"), fallback.defaultVersion());
        assertEquals(ApiVersionMode.FALLBACK, fallback.mode());
        assertEquals(ApiVersionMode.STRICT, strict.mode());
        assertEquals(ApiVersionMode.FALLBACK, new ApiVersionResolver(ApiVersion.parse("1.0.0")).mode());
    }
}
