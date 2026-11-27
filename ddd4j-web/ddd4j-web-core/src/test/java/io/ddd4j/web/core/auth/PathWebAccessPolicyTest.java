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

/** 路径免认证、前缀匹配与默认认证模式契约。 */
class PathWebAccessPolicyTest {

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("r-1", "t-1", "tenant-a", null, Locale.CHINA, "127.0.0.1", "GET", path)</code>
     */
    private static WebRequestContext request(String path) {
        return new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "GET", path);
    }

    /** \u9a8c\u8bc1<code>policy.authenticationMode(request("/health"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\uff0c<code>policy.authenticationMode(request("/health/details"))</code> \u7b49\u4e8e <code>AuthenticationMode.REQUIRED</code>\uff0c<code>policy.authenticationMode(request("/api"))</code> \u7b49\u4e8e <code>AuthenticationMode.REQUIRED</code>\u3002 */
    @Test
    void exactPatternMatchesOnlyExactPath() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(Collections.singletonList("/health"), AuthenticationMode.REQUIRED);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/health/details")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/api")));
    }

    /** \u9a8c\u8bc1<code>policy.authenticationMode(request("/health"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\uff0c<code>policy.authenticationMode(request("/health/readiness"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\uff0c<code>policy.authenticationMode(request("/healthz"))</code> \u7b49\u4e8e <code>AuthenticationMode.REQUIRED</code>\u3002 */
    @Test
    void prefixPatternMatchesPrefixAndSubPaths() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(Collections.singletonList("/health/**"), AuthenticationMode.REQUIRED);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health/readiness")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health/readiness/deep")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/healthz")));
    }

    /** \u9a8c\u8bc1<code>policy.authenticationMode(request("/api"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\uff0c<code>policy.authenticationMode(request("/admin/users"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\uff0c<code>policy.authenticationMode(request("/other"))</code> \u7b49\u4e8e <code>AuthenticationMode.OPTIONAL</code>\u3002 */
    @Test
    void constructorFiltersBlankTrimsAndDeduplicates() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(
                Arrays.asList("  /api  ", "", "   ", "/api", "/admin/**"), AuthenticationMode.OPTIONAL);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/api")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/admin/users")));
        assertEquals(AuthenticationMode.OPTIONAL, policy.authenticationMode(request("/other")));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new PathWebAccessPolicy(null, AuthenticationMode.REQUIRED));
        assertThrows(NullPointerException.class,
                () -> new PathWebAccessPolicy(Collections.singletonList("/health"), null));
    }

    /** \u9a8c\u8bc1<code>policy.authenticationMode(request("/anything"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\u3002 */
    @Test
    void emptyPatternsDisableNothing() {
        PathWebAccessPolicy policy = new PathWebAccessPolicy(Arrays.asList(), AuthenticationMode.DISABLED);
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/anything")));
    }
}
