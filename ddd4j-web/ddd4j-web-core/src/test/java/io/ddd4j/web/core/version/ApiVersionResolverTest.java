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

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ApiVersionResolver} 优先级与双模式测试。
 *
 * <p>对应规格 Scenario（API 版本解析）：请求头优先、路径版本段回退、双缺失回落默认版本、
 * 非法版本值回退默认版本、非法版本值拒绝。
 */
class ApiVersionResolverTest {

    /** \u4f7f\u7528\u9ed8\u8ba4\u56de\u9000\u6a21\u5f0f\u7684 API \u7248\u672c\u89e3\u6790\u5668\u3002 */
    private final ApiVersionResolver fallback =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);

    /** \u4f7f\u7528\u4e25\u683c\u7248\u672c\u89e3\u6790\u6a21\u5f0f\u7684 API \u7248\u672c\u89e3\u6790\u5668\u3002 */
    private final ApiVersionResolver strict =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT);

    /** \u9a8c\u8bc1<code>result.version()</code> \u7b49\u4e8e <code>ApiVersion.parse("2.1.0")</code>\uff0c<code>result.source()</code> \u7b49\u4e8e <code>ApiVersionSource.HEADER</code>\uff0c<code>result.rawValue()</code> \u7b49\u4e8e <code>"2.1.0"</code>\u3002 */
    @Test
    void requestHeaderTakesPriorityOverPathSegment() {
        //规格 Scenario：请求头优先；头与路径不一致时以头为准。
        ApiVersionResult result = fallback.resolve("2.1.0", "/v1/orders");

        assertEquals(ApiVersion.parse("2.1.0"), result.version());
        assertEquals(ApiVersionSource.HEADER, result.source());
        assertEquals("2.1.0", result.rawValue());
    }

    /** \u9a8c\u8bc1<code>first.version()</code> \u7b49\u4e8e <code>ApiVersion.parse("2.0.0")</code>\uff0c<code>first.source()</code> \u7b49\u4e8e <code>ApiVersionSource.PATH</code>\uff0c<code>nested.source()</code> \u7b49\u4e8e <code>ApiVersionSource.PATH</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>fromPath.version()</code> \u7b49\u4e8e <code>ApiVersion.parse("1.0.0")</code>\uff0c<code>fromPath.source()</code> \u7b49\u4e8e <code>ApiVersionSource.DEFAULT</code>\uff0c<code>noPath.source()</code> \u7b49\u4e8e <code>ApiVersionSource.DEFAULT</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>result.version()</code> \u7b49\u4e8e <code>ApiVersion.parse("1.0.0")</code>\uff0c<code>result.source()</code> \u7b49\u4e8e <code>ApiVersionSource.DEFAULT</code>\u3002 */
    @Test
    void illegalHeaderFallsBackToDefaultInFallbackMode() {
        //规格 Scenario：非法版本值回退默认版本（即使路径中存在版本段也回退默认）。
        ApiVersionResult result = fallback.resolve("2.x", "/v9/orders");

        assertEquals(ApiVersion.parse("1.0.0"), result.version());
        assertEquals(ApiVersionSource.DEFAULT, result.source());
    }

    /** \u9a8c\u8bc1<code>strict.resolve("2.x", "/orders")</code> \u629b\u51fa <code>ApiVersionRejectedException.class</code>\uff0c<code>exception.getStatus()</code> \u7b49\u4e8e <code>ApiVersionRejectedException.STATUS</code>\uff0c<code>exception.getMessage().contains("2.x")</code>\u4e3a\u771f\u3002 */
    @Test
    void illegalHeaderIsRejectedInStrictMode() {
        //规格 Scenario：非法版本值拒绝（错误信息指明非法值）。
        ApiVersionRejectedException exception = assertThrows(ApiVersionRejectedException.class,
                () -> strict.resolve("2.x", "/orders"));

        assertEquals(ApiVersionRejectedException.STATUS, exception.getStatus());
        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("2.x"));
    }

    /** \u9a8c\u8bc1<code>result.source()</code> \u7b49\u4e8e <code>ApiVersionSource.DEFAULT</code>\uff0c<code>result.version()</code> \u7b49\u4e8e <code>ApiVersion.parse("1.0.0")</code>\u3002 */
    @Test
    void nonShapedPathSegmentIsNotTreatedAsVersion() {
        ApiVersionResult result = fallback.resolve(null, "/orders/v2-detail");
        assertEquals(ApiVersionSource.DEFAULT, result.source());
        assertEquals(ApiVersion.parse("1.0.0"), result.version());
    }

    /** \u9a8c\u8bc1<code>result.version()</code> \u7b49\u4e8e <code>ApiVersion.parse("2.0.0")</code>\uff0c<code>result.source()</code> \u7b49\u4e8e <code>ApiVersionSource.PATH</code>\u3002 */
    @Test
    void contextOverloadResolvesPathOnly() {
        WebRequestContext context = new WebRequestContext("request-1", null, null, null,
                Locale.CHINA, null, "GET", "/v2/orders");

        ApiVersionResult result = fallback.resolve(context);

        assertEquals(ApiVersion.parse("2.0.0"), result.version());
        assertEquals(ApiVersionSource.PATH, result.source());
    }

    /** \u9a8c\u8bc1<code>fallback.defaultVersion()</code> \u7b49\u4e8e <code>ApiVersion.parse("1.0.0")</code>\uff0c<code>fallback.mode()</code> \u7b49\u4e8e <code>ApiVersionMode.FALLBACK</code>\uff0c<code>strict.mode()</code> \u7b49\u4e8e <code>ApiVersionMode.STRICT</code>\u3002 */
    @Test
    void exposesConfiguredDefaultVersionAndMode() {
        assertEquals(ApiVersion.parse("1.0.0"), fallback.defaultVersion());
        assertEquals(ApiVersionMode.FALLBACK, fallback.mode());
        assertEquals(ApiVersionMode.STRICT, strict.mode());
        assertEquals(ApiVersionMode.FALLBACK, new ApiVersionResolver(ApiVersion.parse("1.0.0")).mode());
    }
}
