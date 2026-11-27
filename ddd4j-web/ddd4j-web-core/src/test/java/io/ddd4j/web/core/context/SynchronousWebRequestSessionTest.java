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

/**
 * 验证 <code>SynchronousWebRequestSession</code> 在<code>open</code>等操作中的行为契约。
 */
@ExtendWith(MockitoExtension.class)
class SynchronousWebRequestSessionTest {

    /**
     * 用于隔离 <code>IdempotencyGuard</code> 调用并记录交互的测试替身。
     */
    @Mock
    private IdempotencyGuard guard;

    /**
     * 创建 <code>new WebRequestContext</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token", Locale.CHINA, "127.0.0.1", "POST", "/api/orders")</code>
     */
    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");
    }

    /**
     * 创建 <code>new WebRequestLifecycle</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled())</code>
     */
    private static WebRequestLifecycle lifecycle() {
        return new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled());
    }

    /**
     * 测试结束后执行 <code>ThreadContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    /**
     * 验证<code>session.requestContext()</code>非空。
     */
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

    /**
     * 在测试框架中执行 <code>guard.acquireLease()</code>、<code>any()</code>、<code>Duration.ofMinutes()</code>，验证该场景。
     */
    @Test
    void openAndCompleteAsFailure() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("k", null, Duration.ofMinutes(5))));
        WebIdempotencyLifecycle idempotencyLifecycle = new WebIdempotencyLifecycle(guard);

        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), idempotencyLifecycle, "order-1");

        session.complete(false);
    }

    /**
     * 验证<code>session.requestContext()</code>非空。
     */
    @Test
    void openWithoutIdempotencyLifecycle() {
        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), null, null);

        assertNotNull(session.requestContext());
        session.complete(true);
    }

    /**
     * 验证<code>SynchronousWebRequestSession.open( null, lifecycle(), null, null)</code> 抛出 <code>NullPointerException.class</code>，<code>SynchronousWebRequestSession.open( request(), null, null, null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void openRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> SynchronousWebRequestSession.open(
                null, lifecycle(), null, null));
        assertThrows(NullPointerException.class, () -> SynchronousWebRequestSession.open(
                request(), null, null, null));
    }

    /**
     * 验证<code>SynchronousWebRequestSession.open( unauthenticated, requiredLifecycle, null, null)</code> 抛出 <code>WebStatusException.class</code>。
     */
    @Test
    void openPropagatesAuthenticationFailure() {
        WebRequestLifecycle requiredLifecycle = new WebRequestLifecycle(
                new BearerSubjectAuthenticator(), WebAccessPolicy.required());
        WebRequestContext unauthenticated = new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");

        assertThrows(WebStatusException.class, () -> SynchronousWebRequestSession.open(
                unauthenticated, requiredLifecycle, null, null));
    }

    /**
     * 验证<code>ThreadContext.get(ContextConstants.TENANT_ID)</code> 等于 <code>"tenant-a"</code>，<code>session.complete(true)</code> 抛出 <code>IllegalStateException.class</code>，<code>ThreadContext.get(ContextConstants.TENANT_ID)</code> 等于 <code>"outer-tenant"</code>。
     */
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
