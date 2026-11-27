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
package io.ddd4j.web.core.context;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.core.idempotency.IdempotencyGuard;
import io.ddd4j.web.core.idempotency.IdempotencyLease;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** \u9a8c\u8bc1 <code>SynchronousWebRequestSession</code> \u5728<code>open</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@ExtendWith(MockitoExtension.class)
class SynchronousWebRequestSessionTest {

    /** \u7528\u4e8e\u9694\u79bb <code>IdempotencyGuard</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private IdempotencyGuard guard;

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token", Locale.CHINA, "127.0.0.1", "POST", "/api/orders")</code>
     */
    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");
    }

    /**
     * \u521b\u5efa <code>new WebRequestLifecycle</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled())</code>
     */
    private static WebRequestLifecycle lifecycle() {
        return new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled());
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    /** \u9a8c\u8bc1<code>session.requestContext()</code>\u975e\u7a7a\u3002 */
    @Test
    void openAndCompleteSuccessfully() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("k", null, Duration.ofMinutes(5))));
        WebIdempotencyLifecycle idempotencyLifecycle = new WebIdempotencyLifecycle(guard);

        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), idempotencyLifecycle, "order-1");

        assertNotNull(session.requestContext());
        session.complete(true);
        session.close();
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>guard.acquireLease()</code>\u3001<code>any()</code>\u3001<code>Duration.ofMinutes()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void openAndCompleteAsFailure() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("k", null, Duration.ofMinutes(5))));
        WebIdempotencyLifecycle idempotencyLifecycle = new WebIdempotencyLifecycle(guard);

        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), idempotencyLifecycle, "order-1");

        session.complete(false);
    }

    /** \u9a8c\u8bc1<code>session.requestContext()</code>\u975e\u7a7a\u3002 */
    @Test
    void openWithoutIdempotencyLifecycle() {
        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), null, null);

        assertNotNull(session.requestContext());
        session.complete(true);
    }

    /** \u9a8c\u8bc1<code>SynchronousWebRequestSession.open( null, lifecycle(), null, null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>SynchronousWebRequestSession.open( request(), null, null, null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void openRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> SynchronousWebRequestSession.open(
                null, lifecycle(), null, null));
        assertThrows(NullPointerException.class, () -> SynchronousWebRequestSession.open(
                request(), null, null, null));
    }

    /** \u9a8c\u8bc1<code>SynchronousWebRequestSession.open( unauthenticated, requiredLifecycle, null, null)</code> \u629b\u51fa <code>WebStatusException.class</code>\u3002 */
    @Test
    void openPropagatesAuthenticationFailure() {
        WebRequestLifecycle requiredLifecycle = new WebRequestLifecycle(
                new BearerSubjectAuthenticator(), WebAccessPolicy.required());
        WebRequestContext unauthenticated = new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");

        assertThrows(WebStatusException.class, () -> SynchronousWebRequestSession.open(
                unauthenticated, requiredLifecycle, null, null));
    }

    /** \u9a8c\u8bc1<code>ThreadContext.get(ContextConstants.TENANT_ID)</code> \u7b49\u4e8e <code>"tenant-a"</code>\uff0c<code>session.complete(true)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>ThreadContext.get(ContextConstants.TENANT_ID)</code> \u7b49\u4e8e <code>"outer-tenant"</code>\u3002 */
    @Test
    void completeFailureRestoresOuterThreadContext() {
        ThreadContext.set(ContextConstants.TENANT_ID, "outer-tenant");
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("k", null, Duration.ofMinutes(5))));
        doThrow(new IllegalStateException("complete failed"))
                .when(guard).complete(any(IdempotencyLease.class));
        WebIdempotencyLifecycle idempotencyLifecycle = new WebIdempotencyLifecycle(guard);
        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), idempotencyLifecycle, "order-1");

        assertEquals("tenant-a", ThreadContext.get(ContextConstants.TENANT_ID));
        assertThrows(IllegalStateException.class, () -> session.complete(true));
        assertEquals("outer-tenant", ThreadContext.get(ContextConstants.TENANT_ID));
    }
}
