package io.ddd4j.data.mybatis.context;

import java.util.Arrays;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.baomidou.mybatisplus.enhance.context.TenantContext;
import io.ddd4j.core.context.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u9a8c\u8bc1 <code>Ddd4jTenantContextConcurrency</code> \u5728 <code>rejectsForeignThreadClose</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class Ddd4jTenantContextConcurrencyTest {

    /** \u7528\u4e8e\u68c0\u67e5\u5f53\u524d\u8bf7\u6c42\u4e0a\u4e0b\u6587\u662f\u5426\u4fdd\u7559\u6216\u5df2\u6e05\u9664\u7684\u6807\u8bb0\u3002 */
    private static final String REQUEST_MARKER = Ddd4jTenantContextConcurrencyTest.class.getName();

    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u6846\u67b6\u4e0a\u4e0b\u6587\u6216\u8bf7\u6c42\u4f5c\u7528\u57df\u3002 */
    private final Ddd4jTenantContext context = new Ddd4jTenantContext();
    /** \u4f9b\u6d4b\u8bd5\u66ff\u8eab\u8f6c\u53d1\u5b9e\u9645\u884c\u4e3a\u7684\u5e95\u5c42\u5b9e\u73b0\u3002 */
    private final TenantContext delegate = context.asEnhanceContext();

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664<code>ThreadContext</code>\u3002 */
    @AfterEach
    void clearRequestContext() {
        ThreadContext.clear();
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>delegate.getCurrentTenantId()</code> \u7b49\u4e8e <code>"owner-original"</code>\u3002
     * @param enhanceEntry \u53c2\u6570\u5316\u7528\u4f8b\u63d0\u4f9b\u7684\u5019\u9009 <code>enhanceEntry</code> \u503c
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsForeignThreadCloseWithoutChangingEitherTenantOrConsumingScope(boolean enhanceEntry) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        context.setCurrentTenantId("owner-original");
        try (AutoCloseable scope = enhanceEntry ? delegate.open("owner-temporary") : context.open("owner-temporary")) {
            executor.submit(() -> {
                context.setCurrentTenantId("worker-tenant");
                try {
                    assertThrows(IllegalStateException.class, scope::close);
                    assertEquals("worker-tenant", delegate.getCurrentTenantId());
                } finally {
                    ThreadContext.clear();
                }
            }).get(10, TimeUnit.SECONDS);
            assertEquals("owner-temporary", delegate.getCurrentTenantId());
        } finally {
            shutdown(executor);
        }
        assertEquals("owner-original", delegate.getCurrentTenantId());
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>delegate.getCurrentTenantId()</code>\u4e3a\u7a7a\u3002
     * @param enhanceEntry \u53c2\u6570\u5316\u7528\u4f8b\u63d0\u4f9b\u7684\u5019\u9009 <code>enhanceEntry</code> \u503c
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsForeignThreadCloseEvenAfterOwnerClosedScope(boolean enhanceEntry) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AutoCloseable scope = enhanceEntry ? delegate.open("temporary") : context.open("temporary");
        scope.close();
        try {
            executor.submit(() -> {
                context.setCurrentTenantId("worker-tenant");
                try {
                    assertThrows(IllegalStateException.class, scope::close);
                    assertEquals("worker-tenant", delegate.getCurrentTenantId());
                } finally {
                    ThreadContext.clear();
                }
            }).get(10, TimeUnit.SECONDS);
            assertNull(delegate.getCurrentTenantId());
        } finally {
            shutdown(executor);
        }
    }

    /**
     * \u9a8c\u8bc1<code>delegate.getCurrentTenantId()</code> \u7b49\u4e8e <code>"original"</code>\uff0c<code>delegate.getCurrentTenantId()</code> \u7b49\u4e8e <code>"subsequent"</code>\u3002
     * @param enhanceEntry \u53c2\u6570\u5316\u7528\u4f8b\u63d0\u4f9b\u7684\u5019\u9009 <code>enhanceEntry</code> \u503c
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedOwnerCloseDoesNotOverwriteSubsequentTenant(boolean enhanceEntry) throws Exception {
        context.setCurrentTenantId("original");
        AutoCloseable scope = enhanceEntry ? delegate.open("temporary") : context.open("temporary");
        scope.close();
        assertEquals("original", delegate.getCurrentTenantId());
        context.setCurrentTenantId("subsequent");
        scope.close();
        assertEquals("subsequent", delegate.getCurrentTenantId());
    }

    /**
     * \u9a8c\u8bc1<code>delegate.getCurrentTenantId()</code> \u7b49\u4e8e <code>tenant</code>\uff0c<code>delegate.getCurrentTenantId()</code>\u4e3a\u7a7a\uff0c<code>ThreadContext.get(REQUEST_MARKER)</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void sharedDelegateKeepsOverlappingThreadScopesIsolated() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CyclicBarrier barrier = new CyclicBarrier(4);
        List<Future<?>> tasks = new ArrayList<>();
        context.setCurrentTenantId("parent-tenant");
        try {
            for (String tenant : Arrays.asList("tenant-a", "tenant-b", "tenant-c", "tenant-d")) {
                tasks.add(executor.submit(() -> {
                    context.setCurrentTenantId("worker-" + tenant);
                    ThreadContext.set(REQUEST_MARKER, tenant);
                    try {
                        for (int round = 0; round < 50; round++) {
                            // 屏障保证四个线程的租户作用域重叠，不能退化成串行验证。
                            try (Ddd4jTenantContext.Scope scope = context.open(tenant)) {
                                barrier.await(10, TimeUnit.SECONDS);
                                assertEquals(tenant, delegate.getCurrentTenantId());
                                try (Ddd4jTenantContext.Scope nested = context.open(null)) {
                                    assertNull(delegate.getCurrentTenantId());
                                    assertEquals(tenant, ThreadContext.get(REQUEST_MARKER));
                                }
                                assertEquals(tenant, delegate.getCurrentTenantId());
                                delegate.setCurrentTenantId("updated-" + tenant);
                                barrier.await(10, TimeUnit.SECONDS);
                                assertEquals("updated-" + tenant, context.getCurrentTenantId());
                                barrier.await(10, TimeUnit.SECONDS);
                            }
                            assertEquals("worker-" + tenant, delegate.getCurrentTenantId());
                        }
                    } finally {
                        ThreadContext.clear();
                    }
                    return null;
                }));
            }
            for (Future<?> task : tasks) {
                task.get(20, TimeUnit.SECONDS);
            }
            assertEquals("parent-tenant", delegate.getCurrentTenantId());
            assertNull(ThreadContext.get(REQUEST_MARKER));
        } finally {
            shutdown(executor);
        }
    }

    /**
     * \u9a8c\u8bc1<code>Thread.currentThread()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>worker</code>\uff0c<code>delegate.getCurrentTenantId()</code>\u4e3a\u7a7a\uff0c<code>error.getCause()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>failure</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void reusedWorkerRestoresTenantAfterSuccessfulAndFailedScopes() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        IllegalArgumentException failure = new IllegalArgumentException("request failed");
        try {
            Thread worker = executor.submit(() -> {
                ThreadContext.clear();
                return Thread.currentThread();
            }).get(10, TimeUnit.SECONDS);
            for (String tenant : Arrays.asList("tenant-a", "tenant-b", "tenant-c")) {
                Future<?> task = executor.submit(() -> {
                    assertSame(worker, Thread.currentThread());
                    assertNull(delegate.getCurrentTenantId());
                    try (Ddd4jTenantContext.Scope scope = context.open(tenant)) {
                        assertEquals(tenant, delegate.getCurrentTenantId());
                        if ("tenant-b".equals(tenant)) {
                            throw failure;
                        }
                    }
                });
                if ("tenant-b".equals(tenant)) {
                    ExecutionException error = assertThrows(ExecutionException.class,
                            () -> task.get(10, TimeUnit.SECONDS));
                    assertSame(failure, error.getCause());
                } else {
                    task.get(10, TimeUnit.SECONDS);
                }
                assertNull(executor.submit(delegate::getCurrentTenantId).get(10, TimeUnit.SECONDS));
            }
        } finally {
            shutdown(executor);
        }
    }

    /**
     * \u9a8c\u8bc1<code>release.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>started.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>delegate.getCurrentTenantId()</code> \u7b49\u4e8e <code>"parent-after-submission"</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void queuedAsyncTasksCaptureSubmissionTenantAndRestoreWorkerAfterFailure() throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = TtlExecutors.getTtlExecutorService(raw);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IllegalArgumentException failure = new IllegalArgumentException("async request failed");
        try {
            // 先启动线程并留下独立上下文，排除建线程时继承父值造成的假阳性。
            raw.submit(() -> context.setCurrentTenantId("worker-baseline")).get(10, TimeUnit.SECONDS);
            Future<?> blocker = raw.submit(() -> {
                started.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return null;
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));

            context.setCurrentTenantId("tenant-a");
            CompletableFuture<Object> first = CompletableFuture.supplyAsync(() -> {
                assertEquals("tenant-a", delegate.getCurrentTenantId());
                delegate.setCurrentTenantId("task-a-only");
                ThreadContext.set(REQUEST_MARKER, "task-a-marker");
                return delegate.getCurrentTenantId();
            }, wrapped);
            Future<Object> afterFirst = raw.submit(delegate::getCurrentTenantId);

            context.setCurrentTenantId("tenant-b");
            CompletableFuture<Object> second = CompletableFuture.supplyAsync(() -> {
                assertEquals("tenant-b", delegate.getCurrentTenantId());
                assertNull(ThreadContext.get(REQUEST_MARKER));
                try (Ddd4jTenantContext.Scope scope = context.open("task-b-only")) {
                    throw failure;
                }
            }, wrapped);
            Future<Object> afterSecond = raw.submit(delegate::getCurrentTenantId);

            context.clear();
            CompletableFuture<Object> anonymous = CompletableFuture.supplyAsync(() -> {
                assertNull(ThreadContext.get(REQUEST_MARKER));
                return delegate.getCurrentTenantId();
            }, wrapped);
            context.setCurrentTenantId("parent-after-submission");
            release.countDown();

            blocker.get(10, TimeUnit.SECONDS);
            assertEquals("task-a-only", first.get(10, TimeUnit.SECONDS));
            assertEquals("worker-baseline", afterFirst.get(10, TimeUnit.SECONDS));
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));
            assertSame(failure, error.getCause());
            assertEquals("worker-baseline", afterSecond.get(10, TimeUnit.SECONDS));
            assertNull(anonymous.get(10, TimeUnit.SECONDS));
            assertEquals("worker-baseline", raw.submit(delegate::getCurrentTenantId).get(10, TimeUnit.SECONDS));
            assertEquals("parent-after-submission", delegate.getCurrentTenantId());
            assertNull(ThreadContext.get(REQUEST_MARKER));
        } finally {
            release.countDown();
            shutdown(raw);
        }
    }

    /**
     * \u9a8c\u8bc1<code>delegate.getCurrentTenantId()</code> \u7b49\u4e8e <code>"request-tenant"</code>\uff0c<code>delegate.getCurrentTenantId()</code> \u7b49\u4e8e <code>"outer-tenant"</code>\uff0c<code>task.get(10, TimeUnit.SECONDS)</code> \u7b49\u4e8e <code>"request-tenant"</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void nestedAsyncTasksRestoreEachWorkerAndDoNotWriteBackToParent() throws Exception {
        ExecutorService outerRaw = Executors.newSingleThreadExecutor();
        ExecutorService innerRaw = Executors.newSingleThreadExecutor();
        ExecutorService outer = TtlExecutors.getTtlExecutorService(outerRaw);
        ExecutorService inner = TtlExecutors.getTtlExecutorService(innerRaw);
        try {
            outerRaw.submit(() -> context.setCurrentTenantId("outer-worker")).get(10, TimeUnit.SECONDS);
            innerRaw.submit(() -> context.setCurrentTenantId("inner-worker")).get(10, TimeUnit.SECONDS);
            context.setCurrentTenantId("request-tenant");

            Future<Object> task = outer.submit(() -> {
                assertEquals("request-tenant", delegate.getCurrentTenantId());
                try (Ddd4jTenantContext.Scope scope = context.open("outer-tenant")) {
                    CompletableFuture<Object> child = CompletableFuture.supplyAsync(() -> {
                        assertEquals("outer-tenant", delegate.getCurrentTenantId());
                        try (Ddd4jTenantContext.Scope nested = context.open("inner-tenant")) {
                            assertEquals("inner-tenant", delegate.getCurrentTenantId());
                        }
                        return delegate.getCurrentTenantId();
                    }, inner);
                    assertEquals("outer-tenant", child.get(10, TimeUnit.SECONDS));
                    assertEquals("outer-tenant", delegate.getCurrentTenantId());
                }
                return delegate.getCurrentTenantId();
            });

            assertEquals("request-tenant", task.get(10, TimeUnit.SECONDS));
            assertEquals("request-tenant", delegate.getCurrentTenantId());
            assertEquals("outer-worker", outerRaw.submit(delegate::getCurrentTenantId).get(10, TimeUnit.SECONDS));
            assertEquals("inner-worker", innerRaw.submit(delegate::getCurrentTenantId).get(10, TimeUnit.SECONDS));
        } finally {
            shutdown(outerRaw);
            shutdown(innerRaw);
        }
    }

    /**
     * \u8c03\u7528 <code>executor.shutdownNow()</code>\u3001<code>executor.awaitTermination()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param executor \u5f53\u524d\u6d4b\u8bd5\u6d89\u53ca\u7684\u547d\u4ee4\u6267\u884c\u5668\u6216\u7ebf\u7a0b\u6267\u884c\u5668
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
}
