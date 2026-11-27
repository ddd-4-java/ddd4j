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

/** \u9a8c\u8bc1 <code>WebAccessPolicy</code> \u5728<code>disabledAlways</code>\u3001<code>optionalAlways</code>\u3001<code>requiredAlways</code>\u3001<code>requiredExcept</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class WebAccessPolicyTest {

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("r-1", "t-1", "tenant-a", null, Locale.CHINA, "127.0.0.1", "GET", path)</code>
     */
    private static WebRequestContext request(String path) {
        return new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "GET", path);
    }

    /** \u9a8c\u8bc1<code>policy.authenticationMode(request("/api"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\u3002 */
    @Test
    void disabledAlwaysReturnsDisabled() {
        WebAccessPolicy policy = WebAccessPolicy.disabled();
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/api")));
    }

    /** \u9a8c\u8bc1<code>policy.authenticationMode(request("/api"))</code> \u7b49\u4e8e <code>AuthenticationMode.OPTIONAL</code>\u3002 */
    @Test
    void optionalAlwaysReturnsOptional() {
        WebAccessPolicy policy = WebAccessPolicy.optional();
        assertEquals(AuthenticationMode.OPTIONAL, policy.authenticationMode(request("/api")));
    }

    /** \u9a8c\u8bc1<code>policy.authenticationMode(request("/api"))</code> \u7b49\u4e8e <code>AuthenticationMode.REQUIRED</code>\u3002 */
    @Test
    void requiredAlwaysReturnsRequired() {
        WebAccessPolicy policy = WebAccessPolicy.required();
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/api")));
    }

    /** \u9a8c\u8bc1<code>policy.authenticationMode(request("/health"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\uff0c<code>policy.authenticationMode(request("/api"))</code> \u7b49\u4e8e <code>AuthenticationMode.REQUIRED</code>\u3002 */
    @Test
    void requiredExceptDisablesPublicPaths() {
        WebAccessPolicy policy = WebAccessPolicy.requiredExcept(path -> path.startsWith("/health"));

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/api")));
    }

    /** \u9a8c\u8bc1<code>WebAccessPolicy.requiredExcept(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void requiredExceptRejectsNullPredicate() {
        assertThrows(NullPointerException.class, () -> WebAccessPolicy.requiredExcept(null));
    }
}
