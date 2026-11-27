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
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.idempotency.IdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>Ddd4jWebMvcInterceptor</code> \u5728<code>shouldRestoreContext</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jWebMvcInterceptorTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    /**
     * \u9a8c\u8bc1<code>interceptor.preHandle(request, response, new Object())</code>\u4e3a\u771f\uff0c<code>ThreadContext.getResources().isEmpty()</code>\u4e3a\u5047\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /** \u9a8c\u8bc1<code>interceptor.preHandle(request, response, new Object())</code>\u4e3a\u771f\uff0c<code>ThreadContext.get("tenant-id")</code> \u7b49\u4e8e <code>"inner-tenant"</code>\uff0c<code>ThreadContext.get("tenant-id")</code> \u7b49\u4e8e <code>"outer-tenant"</code>\u3002 */
    @Test
    void shouldRestoreContextWhenIdempotencyCompletionFails() {
        IdempotencyGuard guard = new IdempotencyGuard() {
            @Override
            public boolean acquire(String key, Duration ttl) {
                return true;
            }

            @Override
            public void complete(String key) {
                throw new IllegalStateException("complete failed");
            }

            @Override
            public void release(String key) {
            }
        };
        Ddd4jWebMvcInterceptor interceptor = new Ddd4jWebMvcInterceptor(
                new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                new WebIdempotencyLifecycle(guard));
        ThreadContext.set("tenant-id", "outer-tenant");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.addHeader(WebHeaders.TENANT_ID, "inner-tenant");
        request.addHeader(WebHeaders.IDEMPOTENCY_KEY, "order-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals("inner-tenant", ThreadContext.get("tenant-id"));
        assertThrows(IllegalStateException.class,
                () -> interceptor.afterCompletion(request, response, new Object(), null));
        assertEquals("outer-tenant", ThreadContext.get("tenant-id"));
    }
}
