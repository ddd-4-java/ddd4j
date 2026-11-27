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
package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler.ViewScheduleHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>GuiceViewManager</code> \u5728<code>schedule</code>\u3001<code>scheduleAtFixedRate</code>\u3001<code>getProjectionStatus</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class GuiceViewManagerTest {

    /** \u7b49\u5f85\u6d4b\u8bd5\u56de\u8c03\u62b5\u8fbe\u7684\u79d2\u6570\u4e0a\u9650\u3002 */
    private static final long CALLBACK_TIMEOUT_SECONDS = 5;

    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u89c6\u56fe\u6216\u8ba4\u8bc1\u7ba1\u7406\u5668\u3002 */
    private final GuiceViewManager manager = new GuiceViewManager();

    /**
     * \u521b\u5efa <code>new ProjectionRunner&lt;&gt;</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param repository \u5f53\u524d\u6d4b\u8bd5\u8bbf\u95ee\u7684\u4ed3\u50a8\u5b9e\u4f8b
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ProjectionRunner&lt;&gt;(new DefaultProjectionService(repository), (streamId,position,size,types)-&gt;EventChunk.empty(position))</code>
     */
    private static ProjectionRunner<Object> emptyRunner(GuiceInMemoryProjectionPositionRepository repository) {
        return new ProjectionRunner<>(new DefaultProjectionService(repository),
                (streamId, position, size, types) -> EventChunk.empty(position));
    }

    /**
     * \u6784\u9020 <code>ProjectionView&lt;Object&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ProjectionView&lt;Object&gt;</code> \u7ed3\u679c
     */
    private static ProjectionView<Object> view(String name) {
        return new ProjectionView<Object>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getCron() {
                return "0/3600";
            }

            @Override
            public Collection<String> getEventTypes() {
                return Collections.emptyList();
            }

            @Override
            public void handleEvents(Collection<Object> events) {
            }
        };
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>manager.stop()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        manager.stop();
    }

    /** \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u5047\uff0c<code>manager.isRunning()</code>\u4e3a\u771f\u3002 */
    @Test
    void startAndStopLifecycle() {
        assertFalse(manager.isRunning());
        manager.start();
        assertTrue(manager.isRunning());
        manager.start();
        assertTrue(manager.isRunning());
        manager.stop();
        assertFalse(manager.isRunning());
        manager.stop();
        assertFalse(manager.isRunning());
        manager.close();
    }

    /** \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u5047\u3002 */
    @Test
    void triggerOnceIsSafe() {
        manager.triggerOnce();
        assertFalse(manager.isRunning());
    }

    /**
     * \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u771f\uff0c<code>handle</code>\u975e\u7a7a\uff0c<code>handle.isActive()</code>\u4e3a\u5047\u3002
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
    @Test
    void scheduleRunsTaskAndHandleCancels() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        ViewScheduleHandle handle = manager.schedule("view-1", "0/1", ran::countDown);

        assertTrue(manager.isRunning());
        assertNotNull(handle);
        assertTrue(ran.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Cron task did not run");
        assertTrue(handle.isActive());
        handle.cancel();
        assertFalse(handle.isActive());
    }

    /**
     * \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u771f\uff0c<code>handle</code>\u975e\u7a7a\uff0c<code>ran.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)</code>\u4e3a\u771f\u3002
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
    @Test
    void scheduleWithStarSlashNSucceeds() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        ViewScheduleHandle handle = manager.schedule("view-1b", "*/2", ran::countDown);
        assertTrue(manager.isRunning());
        assertNotNull(handle);
        assertTrue(ran.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Cron task did not run");
        handle.cancel();
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void scheduleWithEmptyCronThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.schedule("view-2", "", () -> {
            });
        });
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void scheduleWithInvalidCronThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.schedule("view-3", "0/not-a-number", () -> {
            });
        });
    }

    /** \u9a8c\u8bc1<code>handle</code>\u975e\u7a7a\u3002 */
    @Test
    void scheduleWithPlainCronDefaultsToMinute() {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.schedule("view-4", "* * * * *", runs::incrementAndGet);
        assertNotNull(handle);
        handle.cancel();
    }

    /** \u9a8c\u8bc1<code>handle</code>\u975e\u7a7a\u3002 */
    @Test
    void scheduleWithMinuteIntervalSucceeds() {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.schedule("view-5", "5 * * * *", runs::incrementAndGet);
        assertNotNull(handle);
        handle.cancel();
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void scheduleWithUnsupportedCronFormatThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.schedule("view-6", "0 0 2 * * ?", () -> {
            });
        });
    }

    /**
     * \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u771f\uff0c<code>handle</code>\u975e\u7a7a\uff0c<code>handle.isActive()</code>\u4e3a\u5047\u3002
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
    @Test
    void scheduleAtFixedRateRunsTask() throws InterruptedException {
        CountDownLatch runs = new CountDownLatch(2);
        ViewScheduleHandle handle = manager.scheduleAtFixedRate("view-7", 1, runs::countDown);

        assertTrue(manager.isRunning());
        assertNotNull(handle);
        assertTrue(runs.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Fixed-rate task did not run twice");
        assertTrue(handle.isActive());
        handle.cancel();
        assertFalse(handle.isActive());
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void scheduleAtFixedRateWithInvalidIntervalThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.scheduleAtFixedRate("view-8", 0, () -> {
            });
        });
        assertThrows(IllegalArgumentException.class, () -> {
            manager.scheduleAtFixedRate("view-9", -1, () -> {
            });
        });
    }

    /** \u9a8c\u8bc1<code>first.isActive()</code>\u4e3a\u5047\uff0c<code>second.isActive()</code>\u4e3a\u771f\uff0c<code>second.isActive()</code>\u4e3a\u5047\u3002 */
    @Test
    void stopCancelsOutstandingHandlesAndSupportsFreshRestart() {
        ViewScheduleHandle first = manager.scheduleAtFixedRate("owned", 3600, () -> {
        });
        manager.stop();
        assertFalse(first.isActive());
        ViewScheduleHandle second = manager.scheduleAtFixedRate("owned", 3600, () -> {
        });
        assertTrue(second.isActive());
        manager.close();
        assertFalse(second.isActive());
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LinkageError.class</code>\uff0c<code>scheduled.get()</code>\u975e\u7a7a\uff0c<code>managed.isRunning()</code>\u4e3a\u5047\u3002 */
    @Test
    void startupLinkageErrorCancelsOwnedTasksAndResetsRunningState() {
        LinkageError failure = new LinkageError("view wiring failed");
        AtomicReference<ViewScheduleHandle> scheduled = new AtomicReference<>();
        AtomicReference<ViewScheduleHandle> owned = new AtomicReference<>();
        GuiceInMemoryProjectionPositionRepository repo = new GuiceInMemoryProjectionPositionRepository();
        ProjectionRunner<Object> runner = emptyRunner(repo);

        try (GuiceViewManager managed = new GuiceViewManager(1, repo, runner,
                Arrays.asList(view("first"), view("second"))) {
            @Override
            public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
                if ("second".equals(viewName)) {
                    throw failure;
                }
                ViewScheduleHandle first = super.schedule(viewName, cron, task);
                scheduled.set(first);
                owned.set(scheduleAtFixedRate("local", 3600, () -> {
                }));
                return first;
            }
        }) {
            assertSame(failure, assertThrows(LinkageError.class, managed::start));
            assertNotNull(scheduled.get());
            assertNotNull(owned.get());
            assertFalse(scheduled.get().isActive());
            assertFalse(owned.get().isActive());
            assertFalse(managed.isRunning());
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LinkageError.class</code>\uff0c<code>managed.isRunning()</code>\u4e3a\u5047\uff0c<code>cancellationAttempts.get()</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void stopLinkageErrorStillCancelsOwnedTasksAndRetriesExternalHandle() {
        LinkageError failure = new LinkageError("first cancellation failed");
        AtomicInteger cancellationAttempts = new AtomicInteger();
        GuiceInMemoryProjectionPositionRepository repo = new GuiceInMemoryProjectionPositionRepository();
        ProjectionRunner<Object> runner = emptyRunner(repo);

        try (GuiceViewManager managed = new GuiceViewManager(1, repo, runner, Collections.singletonList(view("external"))) {
            @Override
            public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
                return new ViewScheduleHandle() {
                    @Override
                    public void cancel() {
                        if (cancellationAttempts.incrementAndGet() == 1) {
                            throw failure;
                        }
                    }

                    @Override
                    public boolean isActive() {
                        return cancellationAttempts.get() < 2;
                    }
                };
            }
        }) {
            managed.start();
            ViewScheduleHandle owned = managed.scheduleAtFixedRate("local", 3600, () -> {
            });

            assertSame(failure, assertThrows(LinkageError.class, managed::stop));
            assertFalse(managed.isRunning());
            assertFalse(owned.isActive());
            assertEquals(1, cancellationAttempts.get());
            managed.close();
            assertEquals(2, cancellationAttempts.get());
        }
    }

    // --- getProjectionStatus tests ---

    /** \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>status.nextEventNumber()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>status.lastError()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getProjectionStatus_withoutRepository_returnsBaseline() {
        manager.start();
        ProjectionStatus status = manager.getProjectionStatus("orders");

        assertEquals("orders", status.streamId());
        assertEquals(0L, status.nextEventNumber());
        assertTrue(status.running());
        assertNull(status.lastRunAt());
        assertEquals(0, status.lastEventCount());
        assertNull(status.lastError());
    }

    /** \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>status.nextEventNumber()</code> \u7b49\u4e8e <code>42L</code>\uff0c<code>status.lastError()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getProjectionStatus_withRepositoryAndPosition_returnsRealPosition() {
        GuiceInMemoryProjectionPositionRepository repo = new GuiceInMemoryProjectionPositionRepository();
        repo.save(new DefaultProjectionPosition("orders", 42L));

        GuiceViewManager mgr = new GuiceViewManager(2, repo);
        mgr.start();

        ProjectionStatus status = mgr.getProjectionStatus("orders");

        assertEquals("orders", status.streamId());
        assertEquals(42L, status.nextEventNumber());
        assertTrue(status.running());
        assertNull(status.lastRunAt());
        assertEquals(0, status.lastEventCount());
        assertNull(status.lastError());

        mgr.stop();
    }

    /** \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>status.nextEventNumber()</code> \u7b49\u4e8e <code>42L</code>\uff0c<code>status.lastError()</code> \u7b49\u4e8e <code>"last failure"</code>\u3002 */
    @Test
    void getProjectionStatus_withMetrics_returnsLastRunInfo() {
        GuiceInMemoryProjectionPositionRepository repo = new GuiceInMemoryProjectionPositionRepository();
        repo.save(new DefaultProjectionPosition("orders", 42L));
        Instant lastRunAt = Instant.parse("2026-10-18T00:00:00Z");
        ProjectionMetrics metrics = new ProjectionMetrics() {
            @Override
            public Optional<ProjectionRunInfo> getLastRunInfo(String streamId) {
                return Optional.of(new ProjectionRunInfo(lastRunAt, 3, "last failure"));
            }
        };
        GuiceViewManager mgr = new GuiceViewManager(2, repo, metrics);

        ProjectionStatus status = mgr.getProjectionStatus("orders");

        assertEquals("orders", status.streamId());
        assertEquals(42L, status.nextEventNumber());
        assertEquals(lastRunAt, status.lastRunAt());
        assertEquals(3, status.lastEventCount());
        assertEquals("last failure", status.lastError());
    }

    /** \u9a8c\u8bc1<code>status.streamId()</code> \u7b49\u4e8e <code>"unknown"</code>\uff0c<code>status.nextEventNumber()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>status.running()</code>\u4e3a\u771f\u3002 */
    @Test
    void getProjectionStatus_withRepositoryButNoPosition_returnsZeroPosition() {
        GuiceInMemoryProjectionPositionRepository repo = new GuiceInMemoryProjectionPositionRepository();

        GuiceViewManager mgr = new GuiceViewManager(2, repo);
        mgr.start();

        ProjectionStatus status = mgr.getProjectionStatus("unknown");

        assertEquals("unknown", status.streamId());
        assertEquals(0L, status.nextEventNumber());
        assertTrue(status.running());

        mgr.stop();
    }

    /** \u9a8c\u8bc1<code>status.running()</code>\u4e3a\u5047\u3002 */
    @Test
    void getProjectionStatus_whenStopped_runningIsFalse() {
        ProjectionStatus status = manager.getProjectionStatus("orders");

        assertFalse(status.running());
    }
}
