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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 <code>WebAccessPolicy</code> 在<code>disabledAlways</code>、<code>optionalAlways</code>、<code>requiredAlways</code>、<code>requiredExcept</code>等操作中的行为契约。
 */
class WebAccessPolicyTest {

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
     * 验证<code>policy.authenticationMode(request("/api"))</code> 等于 <code>AuthenticationMode.DISABLED</code>。
     */
    @Test
    void disabledAlwaysReturnsDisabled() {
        WebAccessPolicy policy = WebAccessPolicy.disabled();
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/api")));
    }

    /**
     * 验证<code>policy.authenticationMode(request("/api"))</code> 等于 <code>AuthenticationMode.OPTIONAL</code>。
     */
    @Test
    void optionalAlwaysReturnsOptional() {
        WebAccessPolicy policy = WebAccessPolicy.optional();
        assertEquals(AuthenticationMode.OPTIONAL, policy.authenticationMode(request("/api")));
    }

    /**
     * 验证<code>policy.authenticationMode(request("/api"))</code> 等于 <code>AuthenticationMode.REQUIRED</code>。
     */
    @Test
    void requiredAlwaysReturnsRequired() {
        WebAccessPolicy policy = WebAccessPolicy.required();
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/api")));
    }

    /**
     * 验证<code>policy.authenticationMode(request("/health"))</code> 等于 <code>AuthenticationMode.DISABLED</code>，<code>policy.authenticationMode(request("/api"))</code> 等于 <code>AuthenticationMode.REQUIRED</code>。
     */
    @Test
    void requiredExceptDisablesPublicPaths() {
        WebAccessPolicy policy = WebAccessPolicy.requiredExcept(path -> path.startsWith("/health"));

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/api")));
    }

    /**
     * 验证<code>WebAccessPolicy.requiredExcept(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void requiredExceptRejectsNullPredicate() {
        assertThrows(NullPointerException.class, () -> WebAccessPolicy.requiredExcept(null));
    }
}
