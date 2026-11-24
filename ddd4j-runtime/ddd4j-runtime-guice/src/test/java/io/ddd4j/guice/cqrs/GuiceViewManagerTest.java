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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunInfo;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler.ViewScheduleHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GuiceViewManagerTest {

    private static final long CALLBACK_TIMEOUT_SECONDS = 5;

    private final GuiceViewManager manager = new GuiceViewManager();

    @AfterEach
    void tearDown() {
        manager.stop();
    }

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

    @Test
    void triggerOnceIsSafe() {
        manager.triggerOnce();
        assertFalse(manager.isRunning());
    }

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

    @Test
    void scheduleWithStarSlashNSucceeds() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        ViewScheduleHandle handle = manager.schedule("view-1b", "*/2", ran::countDown);
        assertTrue(manager.isRunning());
        assertNotNull(handle);
        assertTrue(ran.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Cron task did not run");
        handle.cancel();
    }

    @Test
    void scheduleWithEmptyCronThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.schedule("view-2", "", () -> {
            });
        });
    }

    @Test
    void scheduleWithInvalidCronThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.schedule("view-3", "0/not-a-number", () -> {
            });
        });
    }

    @Test
    void scheduleWithPlainCronDefaultsToMinute() {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.schedule("view-4", "* * * * *", runs::incrementAndGet);
        assertNotNull(handle);
        handle.cancel();
    }

    @Test
    void scheduleWithMinuteIntervalSucceeds() {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.schedule("view-5", "5 * * * *", runs::incrementAndGet);
        assertNotNull(handle);
        handle.cancel();
    }

    @Test
    void scheduleWithUnsupportedCronFormatThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.schedule("view-6", "0 0 2 * * ?", () -> {
            });
        });
    }

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

    @Test
    void stopCancelsOutstandingHandlesAndSupportsFreshRestart() {
        ViewScheduleHandle first = manager.scheduleAtFixedRate("owned", 3600, () -> { });
        manager.stop();
        assertFalse(first.isActive());
        ViewScheduleHandle second = manager.scheduleAtFixedRate("owned", 3600, () -> { });
        assertTrue(second.isActive());
        manager.close();
        assertFalse(second.isActive());
    }

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
                owned.set(scheduleAtFixedRate("local", 3600, () -> { }));
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
            ViewScheduleHandle owned = managed.scheduleAtFixedRate("local", 3600, () -> { });

            assertSame(failure, assertThrows(LinkageError.class, managed::stop));
            assertFalse(managed.isRunning());
            assertFalse(owned.isActive());
            assertEquals(1, cancellationAttempts.get());
            managed.close();
            assertEquals(2, cancellationAttempts.get());
        }
    }

    private static ProjectionRunner<Object> emptyRunner(GuiceInMemoryProjectionPositionRepository repository) {
        return new ProjectionRunner<>(new DefaultProjectionService(repository),
                (streamId, position, size, types) -> EventChunk.empty(position));
    }

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

    // --- getProjectionStatus tests ---

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

    @Test
    void getProjectionStatus_whenStopped_runningIsFalse() {
        ProjectionStatus status = manager.getProjectionStatus("orders");

        assertFalse(status.running());
    }
}
