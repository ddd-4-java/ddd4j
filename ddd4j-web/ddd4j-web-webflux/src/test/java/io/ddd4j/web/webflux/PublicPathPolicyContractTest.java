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
package io.ddd4j.web.webflux;

import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** \u901a\u8fc7 <code>mock()</code>\u3001<code>environment.getProperty()</code>\u3001<code>eq()</code> \u9a8c\u8bc1 <code>PublicPathPolicyContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class PublicPathPolicyContractTest {

    /** \u9a8c\u8bc1<code>policy.authenticationMode(request("/health"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\uff0c<code>policy.authenticationMode(request("/assets/file.js"))</code> \u7b49\u4e8e <code>AuthenticationMode.DISABLED</code>\uff0c<code>policy.authenticationMode(request("/private"))</code> \u7b49\u4e8e <code>AuthenticationMode.REQUIRED</code>\u3002 */
    @Test
    void nullAndBlankConfiguredPathsAreFilteredBeforeMatching() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty(eq("ddd4j.web.public-paths"), eq(String[].class), any(String[].class)))
                .thenReturn(new String[]{null, " /health ", "", "/assets/**"});

        WebAccessPolicy policy = new DefaultWebFluxConfiguration().webAccessPolicy(environment);

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/assets/file.js")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/private")));
    }

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("request", "trace", null, null, Locale.ROOT, null, "GET", path)</code>
     */
    private static WebRequestContext request(String path) {
        return new WebRequestContext("request", "trace", null, null, Locale.ROOT, null, "GET", path);
    }
}
