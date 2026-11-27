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
package io.ddd4j.web.core.idempotency;

import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.error.WebStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证幂等租约的获取、完成和释放。
 */
@ExtendWith(MockitoExtension.class)
class WebIdempotencyLifecycleTest {

    /**
     * 用于隔离 <code>IdempotencyGuard</code> 调用并记录交互的测试替身。
     */
    @Mock
    private IdempotencyGuard guard;

    /**
     * 创建 <code>new WebRequestContext</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new WebRequestContext("r-1", "t-1", "tenant-a", null, Locale.CHINA, "127.0.0.1", "POST", "/api/orders")</code>
     */
    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");
    }

    /**
     * 验证<code>lifecycle.open(request(), " ").isPresent()</code>为假，<code>lifecycle.open(request(), null).isPresent()</code>为假。
     */
    @Test
    void openReturnsEmptyForBlankKey() {
        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        assertFalse(lifecycle.open(request(), "  ").isPresent());
        assertFalse(lifecycle.open(request(), null).isPresent());
    }

    /**
     * 验证<code>scope.isPresent()</code>为真。
     */
    @Test
    void openAcquiresLeaseAndScopeCompletesAndCloses() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("tenant-a:POST:/api/orders:order-1",
                        null, Duration.ofMinutes(5))));

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        Optional<WebIdempotencyLifecycle.Scope> scope = lifecycle.open(request(), "order-1");

        assertTrue(scope.isPresent());
        scope.get().complete();
        verify(guard).complete(any(IdempotencyLease.class));
        scope.get().close();
    }

    /**
     * 在测试框架中执行 <code>guard.acquireLease()</code>、<code>any()</code>、<code>Duration.ofMinutes()</code>，验证该场景。
     */
    @Test
    void closeWithoutCompleteReleasesLease() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("tenant-a:POST:/api/orders:order-1",
                        null, Duration.ofMinutes(5))));

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        Optional<WebIdempotencyLifecycle.Scope> scope = lifecycle.open(request(), "order-1");

        scope.get().close();
        verify(guard).release(any(IdempotencyLease.class));
    }

    /**
     * 验证<code>lifecycle.open(request(), "order-1")</code> 抛出 <code>WebStatusException.class</code>。
     */
    @Test
    void openThrowsConflictWhenLeaseUnavailable() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.empty());

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        assertThrows(WebStatusException.class, () -> lifecycle.open(request(), "order-1"));
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>，被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void constructorValidatesArguments() {
        assertThrows(NullPointerException.class, () -> new WebIdempotencyLifecycle(null));
        assertThrows(NullPointerException.class,
                () -> new WebIdempotencyLifecycle(guard, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WebIdempotencyLifecycle(guard, Duration.ZERO));
    }

    /**
     * 验证<code>lifecycle.open(null, "order-1")</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void openRejectsNullContextWithKey() {
        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        assertThrows(NullPointerException.class, () -> lifecycle.open(null, "order-1"));
    }

    /**
     * 验证<code>scope.isPresent()</code>为真。
     */
    @Test
    void openUsesUnderscoreForBlankStorageValues() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("_:_:/:order-1", null, Duration.ofMinutes(5))));
        WebRequestContext blank = new WebRequestContext("r-1", null, null, null,
                null, null, null, null);

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        Optional<WebIdempotencyLifecycle.Scope> scope = lifecycle.open(blank, "order-1");

        assertTrue(scope.isPresent());
        verify(guard).acquireLease("_:_:/:order-1", Duration.ofMinutes(5));
        scope.get().close();
    }

    /**
     * 在测试框架中执行 <code>guard.acquireLease()</code>、<code>any()</code>、<code>Duration.ofMinutes()</code>，验证该场景。
     */
    @Test
    void scopeCompleteAfterCloseIsNoOp() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("tenant-a:POST:/api/orders:order-1",
                        null, Duration.ofMinutes(5))));

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        WebIdempotencyLifecycle.Scope scope = lifecycle.open(request(), "order-1").get();

        scope.close();
        scope.complete();

        verify(guard).release(any(IdempotencyLease.class));
        verify(guard, never()).complete(any(IdempotencyLease.class));
    }

    /**
     * 在测试框架中执行 <code>guard.acquireLease()</code>、<code>any()</code>、<code>Duration.ofMinutes()</code>，验证该场景。
     */
    @Test
    void scopeCloseTwiceIsNoOp() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("tenant-a:POST:/api/orders:order-1",
                        null, Duration.ofMinutes(5))));

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        WebIdempotencyLifecycle.Scope scope = lifecycle.open(request(), "order-1").get();

        scope.close();
        scope.close();

        verify(guard, times(1)).release(any(IdempotencyLease.class));
    }

    /**
     * 验证<code>finishCompletion.await(2, TimeUnit.SECONDS)</code>为真，<code>completing.await(2, TimeUnit.SECONDS)</code>为真，<code>closing.await(2, TimeUnit.SECONDS)</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void closeCannotReleaseLeaseWhileCompletionIsInProgress() throws Exception {
        IdempotencyLease lease = new IdempotencyLease("tenant-a:POST:/api/orders:order-1",
                "owner-1", Duration.ofMinutes(5));
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(lease));
        CountDownLatch completing = new CountDownLatch(1);
        CountDownLatch finishCompletion = new CountDownLatch(1);
        doAnswer(invocation -> {
            completing.countDown();
            assertTrue(finishCompletion.await(2, TimeUnit.SECONDS));
            return null;
        }).when(guard).complete(lease);
        WebIdempotencyLifecycle.Scope scope =
                new WebIdempotencyLifecycle(guard).open(request(), "order-1").get();

        CompletableFuture<Void> completingTask = CompletableFuture.runAsync(scope::complete);
        try {
            assertTrue(completing.await(2, TimeUnit.SECONDS));
            CountDownLatch closing = new CountDownLatch(1);
            CompletableFuture<Void> closingTask = CompletableFuture.runAsync(() -> {
                closing.countDown();
                scope.close();
            });
            assertTrue(closing.await(2, TimeUnit.SECONDS));
            TimeUnit.MILLISECONDS.sleep(50);
            finishCompletion.countDown();
            completingTask.get(2, TimeUnit.SECONDS);
            closingTask.get(2, TimeUnit.SECONDS);
        } finally {
            finishCompletion.countDown();
        }

        verify(guard).complete(lease);
        verify(guard, never()).release(lease);
    }
}
