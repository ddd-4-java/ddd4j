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

class PublicPathPolicyContractTest {

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

    private static WebRequestContext request(String path) {
        return new WebRequestContext("request", "trace", null, null, Locale.ROOT, null, "GET", path);
    }
}
