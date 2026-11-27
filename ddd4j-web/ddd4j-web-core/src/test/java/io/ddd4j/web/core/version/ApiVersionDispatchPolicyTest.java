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

import io.ddd4j.web.core.error.WebStatusException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApiVersionDispatchPolicy} 分发规则测试。
 *
 * <p>对应规格 Scenario（版本分发策略）：主版本分流、次版本向后兼容、
 * 请求低于最低可用版本（默认优雅回落与严格拒绝双模式）、主版本无注册返回 404。
 */
class ApiVersionDispatchPolicyTest {

    /** \u7528\u4e8e\u8986\u76d6\u56de\u9000\u8def\u5f84\u7684 Web \u8bbf\u95ee\u7b56\u7565\u3002 */
    private final ApiVersionDispatchPolicy fallbackPolicy = new ApiVersionDispatchPolicy(ApiVersionMode.FALLBACK);

    /** \u8981\u6c42\u4e25\u683c\u5339\u914d\u8bbf\u95ee\u89c4\u5219\u7684\u7b56\u7565\u5b9e\u4f8b\u3002 */
    private final ApiVersionDispatchPolicy strictPolicy = new ApiVersionDispatchPolicy(ApiVersionMode.STRICT);

    /** \u9a8c\u8bc1<code>result.matched()</code>\u4e3a\u771f\uff0c<code>result.matchedVersion()</code> \u7b49\u4e8e <code>ApiVersion.parse("2.0.0")</code>\uff0c<code>result.requireMatch()</code> \u7b49\u4e8e <code>"handler-v2"</code>\u3002 */
    @Test
    void majorVersionSplitsDispatch() {
        //规格 Scenario：主版本分流（请求 v2 不会落到 v1 处理器）。
        ApiVersionRegistry<String> registry = new ApiVersionRegistry<String>()
                .register("1.0.0", "handler-v1")
                .register("2.0.0", "handler-v2");

        ApiVersionDispatchResult<String> result = fallbackPolicy.dispatch(ApiVersion.parse("2.1.0"), registry);

        assertTrue(result.matched());
        assertEquals(ApiVersion.parse("2.0.0"), result.matchedVersion());
        assertEquals("handler-v2", result.handler());
        assertFalse(result.degraded());
        assertFalse(result.upgradeHint());
        assertEquals(ApiVersionDispatchResult.STATUS_OK, result.status());
        assertEquals("handler-v2", result.requireMatch());
    }

    /** \u9a8c\u8bc1<code>belowHighest.matched()</code>\u4e3a\u771f\uff0c<code>belowHighest.matchedVersion()</code> \u7b49\u4e8e <code>ApiVersion.parse("2.1.0")</code>\uff0c<code>lowest.degraded()</code>\u4e3a\u5047\u3002 */
    @Test
    void picksHighestVersionNotAboveRequested() {
        //规格 Scenario：次版本向后兼容（2.0、2.1、2.3 注册，请求 2.2 → 命中 2.1）。
        ApiVersionRegistry<String> registry = new ApiVersionRegistry<String>()
                .register("2.0", "h20")
                .register("2.1", "h21")
                .register("2.3", "h23");

        ApiVersionDispatchResult<String> belowHighest = fallbackPolicy.dispatch(ApiVersion.parse("2.2.0"), registry);
        assertTrue(belowHighest.matched());
        assertEquals(ApiVersion.parse("2.1.0"), belowHighest.matchedVersion());
        assertEquals("h21", belowHighest.handler());
        assertFalse(belowHighest.degraded());
        assertEquals(200, belowHighest.status());

        ApiVersionDispatchResult<String> exact = fallbackPolicy.dispatch(ApiVersion.parse("2.3.0"), registry);
        assertEquals(ApiVersion.parse("2.3.0"), exact.matchedVersion());
        assertEquals("h23", exact.handler());

        ApiVersionDispatchResult<String> lowest = fallbackPolicy.dispatch(ApiVersion.parse("2.0.0"), registry);
        assertEquals(ApiVersion.parse("2.0.0"), lowest.matchedVersion());
        assertEquals("h20", lowest.handler());
        assertFalse(lowest.degraded());
    }

    /** \u9a8c\u8bc1<code>result.matched()</code>\u4e3a\u771f\uff0c<code>result.matchedVersion()</code> \u7b49\u4e8e <code>ApiVersion.parse("2.5.0")</code>\uff0c<code>result.message().contains("2.5.0")</code>\u4e3a\u771f\u3002 */
    @Test
    void gracefulFallbackToLowestWithUpgradeHint() {
        //规格 Scenario：同主版本仅注册 2.5、2.6，请求 2.1 → 优雅回落 2.5 并带升级提示（默认模式）。
        ApiVersionRegistry<String> registry = new ApiVersionRegistry<String>()
                .register("2.5", "h25")
                .register("2.6", "h26");

        ApiVersionDispatchResult<String> result = fallbackPolicy.dispatch(ApiVersion.parse("2.1.0"), registry);

        assertTrue(result.matched());
        assertEquals(ApiVersion.parse("2.5.0"), result.matchedVersion());
        assertEquals("h25", result.handler());
        assertTrue(result.degraded());
        assertTrue(result.upgradeHint());
        assertEquals(200, result.status());
        assertTrue(result.message().contains("2.5.0"));
    }

    /** \u9a8c\u8bc1<code>result.matched()</code>\u4e3a\u5047\uff0c<code>result.matchedVersion()</code>\u4e3a\u7a7a\uff0c<code>exception.getMessage().contains("2.5.0")</code>\u4e3a\u771f\u3002 */
    @Test
    void strictModeRejectsBelowLowestAvailableVersion() {
        //规格 Scenario：严格模式下请求 2.1 且最低可用为 2.5 → 4xx 拒绝。
        ApiVersionRegistry<String> registry = new ApiVersionRegistry<String>()
                .register("2.5", "h25")
                .register("2.6", "h26");

        ApiVersionDispatchResult<String> result = strictPolicy.dispatch(ApiVersion.parse("2.1.0"), registry);

        assertFalse(result.matched());
        assertNull(result.matchedVersion());
        assertNull(result.handler());
        assertFalse(result.degraded());
        assertEquals(ApiVersionDispatchResult.STATUS_REJECTED, result.status());
        assertEquals(400, result.status());
        WebStatusException exception = assertThrows(WebStatusException.class, result::requireMatch);
        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("2.5.0"));
    }

    /** \u9a8c\u8bc1<code>result.matched()</code>\u4e3a\u5047\uff0c<code>result.matchedVersion()</code>\u4e3a\u7a7a\uff0c<code>exception.getMessage().contains("major version 3")</code>\u4e3a\u771f\u3002 */
    @Test
    void majorVersionWithoutRegistrationReturns404() {
        //规格 Scenario：主版本无注册（请求 3.0，仅注册 v1/v2）→ 404 语义。
        ApiVersionRegistry<String> registry = new ApiVersionRegistry<String>()
                .register("1.0.0", "handler-v1")
                .register("2.0.0", "handler-v2");

        ApiVersionDispatchResult<String> result = fallbackPolicy.dispatch(ApiVersion.parse("3.0.0"), registry);

        assertFalse(result.matched());
        assertNull(result.matchedVersion());
        assertNull(result.handler());
        assertEquals(ApiVersionDispatchResult.STATUS_NOT_FOUND, result.status());
        assertEquals(404, result.status());
        WebStatusException exception = assertThrows(WebStatusException.class, result::requireMatch);
        assertEquals(404, exception.getStatus());
        assertTrue(exception.getMessage().contains("major version 3"));
    }

    /** \u9a8c\u8bc1<code>registry.isEmpty()</code>\u4e3a\u5047\uff0c<code>registry.size()</code> \u7b49\u4e8e <code>3</code>\uff0c<code>registry.register("bad-version", "handler")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void registryExposesSortedSnapshotsAndDetectsDuplicates() {
        ApiVersionRegistry<String> registry = new ApiVersionRegistry<String>()
                .register(ApiVersion.parse("2.10.0"), "h210")
                .register("v2.9.0", "h29")
                .register("1.0.0", "h1");

        assertFalse(registry.isEmpty());
        assertEquals(3, registry.size());
        assertEquals(Arrays.asList(ApiVersion.parse("1.0.0"), ApiVersion.parse("2.9.0"), ApiVersion.parse("2.10.0")),
                registry.versions());
        assertEquals(Arrays.asList(ApiVersion.parse("2.9.0"), ApiVersion.parse("2.10.0")),
                registry.versionsOfMajor(2));
        assertEquals(0, registry.versionsOfMajor(5).size());
        assertEquals("h210", registry.find(ApiVersion.parse("2.10.0")));
        assertNull(registry.find(ApiVersion.parse("3.0.0")));
        assertThrows(IllegalArgumentException.class, () -> registry.register("1.0.0", "duplicate"));
        assertThrows(IllegalArgumentException.class, () -> registry.register(ApiVersion.parse("1.0.0"), "duplicate"));
        assertThrows(IllegalArgumentException.class, () -> registry.register("bad-version", "handler"));
    }

    /** \u9a8c\u8bc1<code>result.status()</code> \u7b49\u4e8e <code>404</code>\uff0c<code>result.matched()</code>\u4e3a\u5047\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void emptyRegistryReportsNotFoundAndExposesMode() {
        ApiVersionRegistry<String> empty = new ApiVersionRegistry<String>();
        ApiVersionDispatchResult<String> result = fallbackPolicy.dispatch(ApiVersion.parse("1.0.0"), empty);
        assertEquals(404, result.status());
        assertFalse(result.matched());

        assertEquals(ApiVersionMode.FALLBACK, fallbackPolicy.mode());
        assertEquals(ApiVersionMode.STRICT, strictPolicy.mode());
        assertEquals(ApiVersionMode.FALLBACK, new ApiVersionDispatchPolicy().mode());
        assertThrows(NullPointerException.class, () -> new ApiVersionDispatchPolicy(null));
    }
}
