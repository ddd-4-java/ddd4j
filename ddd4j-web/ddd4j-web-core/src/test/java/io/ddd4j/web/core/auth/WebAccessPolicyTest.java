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

class WebAccessPolicyTest {

    private static WebRequestContext request(String path) {
        return new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "GET", path);
    }

    @Test
    void disabledAlwaysReturnsDisabled() {
        WebAccessPolicy policy = WebAccessPolicy.disabled();
        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/api")));
    }

    @Test
    void optionalAlwaysReturnsOptional() {
        WebAccessPolicy policy = WebAccessPolicy.optional();
        assertEquals(AuthenticationMode.OPTIONAL, policy.authenticationMode(request("/api")));
    }

    @Test
    void requiredAlwaysReturnsRequired() {
        WebAccessPolicy policy = WebAccessPolicy.required();
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/api")));
    }

    @Test
    void requiredExceptDisablesPublicPaths() {
        WebAccessPolicy policy = WebAccessPolicy.requiredExcept(path -> path.startsWith("/health"));

        assertEquals(AuthenticationMode.DISABLED, policy.authenticationMode(request("/health")));
        assertEquals(AuthenticationMode.REQUIRED, policy.authenticationMode(request("/api")));
    }

    @Test
    void requiredExceptRejectsNullPredicate() {
        assertThrows(NullPointerException.class, () -> WebAccessPolicy.requiredExcept(null));
    }
}
