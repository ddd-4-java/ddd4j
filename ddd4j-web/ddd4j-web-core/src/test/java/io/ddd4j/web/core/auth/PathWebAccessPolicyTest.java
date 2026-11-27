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
package io.ddd4j.web.core.auth;

import io.ddd4j.web.core.context.WebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 路径免认证、前缀匹配与默认认证模式契约。
 */
class PathWebAccessPolicyTest {

    /**
     * 创建 <code>new WebRequestContext</code> 供当前测试路径调用。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>new WebRequestContext("r-1", "t-1", "tenant-a", null, Locale.CHINA, "127.0.0.1", "GET", path)</code>
     */
    private static WebRequestContext request(String path) {
        return new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "GET", path);
    }

    /**
     * 验证<code>policy.authenticationMode(request("/health"))</code> 等于 <code>AuthenticationMode.DISABLED</code>，<code>policy.authenticationMode(request("/health/details"))</code> 等于 <code>AuthenticationMode.REQUIRED</code>，<code>policy.authenticationMode(request("/api"))</code> 等于 <code>AuthenticationMode.REQUIRED</code>。
     */
    @Test
    void exactPatternMatchesOnlyExactPath() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(Collections.singletonList("/health"), AuthenticationMode.REQUIRED);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/health/details")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/api")));
    }

    /**
     * 验证<code>policy.authenticationMode(request("/health"))</code> 等于 <code>AuthenticationMode.DISABLED</code>，<code>policy.authenticationMode(request("/health/readiness"))</code> 等于 <code>AuthenticationMode.DISABLED</code>，<code>policy.authenticationMode(request("/healthz"))</code> 等于 <code>AuthenticationMode.REQUIRED</code>。
     */
    @Test
    void prefixPatternMatchesPrefixAndSubPaths() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(Collections.singletonList("/health/**"), AuthenticationMode.REQUIRED);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health/readiness")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health/readiness/deep")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/healthz")));
    }

    /**
     * 验证<code>policy.authenticationMode(request("/api"))</code> 等于 <code>AuthenticationMode.DISABLED</code>，<code>policy.authenticationMode(request("/admin/users"))</code> 等于 <code>AuthenticationMode.DISABLED</code>，<code>policy.authenticationMode(request("/other"))</code> 等于 <code>AuthenticationMode.OPTIONAL</code>。
     */
    @Test
    void constructorFiltersBlankTrimsAndDeduplicates() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(
                Arrays.asList("  /api  ", "", "   ", "/api", "/admin/**"), AuthenticationMode.OPTIONAL);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/api")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/admin/users")));
        assertEquals(AuthenticationMode.OPTIONAL, policy.authenticationMode(request("/other")));
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new PathWebAccessPolicy(null, AuthenticationMode.REQUIRED));
        assertThrows(NullPointerException.class,
                () -> new PathWebAccessPolicy(Collections.singletonList("/health"), null));
    }

    /**
     * 验证<code>policy.authenticationMode(request("/anything"))</code> 等于 <code>AuthenticationMode.DISABLED</code>。
     */
    @Test
    void emptyPatternsDisableNothing() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(Arrays.asList(), AuthenticationMode.DISABLED);
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/anything")));
    }
}
