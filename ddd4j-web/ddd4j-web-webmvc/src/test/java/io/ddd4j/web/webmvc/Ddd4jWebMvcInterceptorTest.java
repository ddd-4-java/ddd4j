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
package io.ddd4j.web.webmvc;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddd4jWebMvcInterceptorTest {

    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    @Test
    void shouldBindAndClearPublicRequestContext() throws Exception {
        Ddd4jWebMvcInterceptor interceptor = new Ddd4jWebMvcInterceptor(new BearerSubjectAuthenticator(),
                path -> "/health".equals(path));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertFalse(ThreadContext.getResources().isEmpty());
        assertEquals(ThreadContext.get(WebContextScope.REQUEST_ID, String.class).get(),
                response.getHeader(WebHeaders.REQUEST_ID));

        interceptor.afterCompletion(request, response, new Object(), null);
        assertTrue(ThreadContext.getResources().isEmpty());
    }
}
