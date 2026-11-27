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
import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunInfo;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler.ViewScheduleHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>GuiceViewManager</code> \u5728<code>schedule</code>\u3001<code>scheduleAtFixedRate</code>\u3001<code>getProjectionStatus</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class GuiceViewManagerTest {

    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u89c6\u56fe\u6216\u8ba4\u8bc1\u7ba1\u7406\u5668\u3002 */
    private final GuiceViewManager manager = new GuiceViewManager();

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
     * 验证<code>manager.isRunning()</code>为真，<code>handle</code>非空，<code>runs.get() &gt;= 1</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void scheduleRunsTaskAndHandleCancels() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.schedule("view-1", "0/1", runs::incrementAndGet);

        assertTrue(manager.isRunning());
        assertNotNull(handle);
        Thread.sleep(1500);
        assertTrue(runs.get() >= 1);
        assertTrue(handle.isActive());
        handle.cancel();
        assertFalse(handle.isActive());
    }

    /**
     * 验证<code>manager.isRunning()</code>为真，<code>handle</code>非空，<code>runs.get() &gt;= 1</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void scheduleWithStarSlashNSucceeds() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.schedule("view-1b", "*/2", runs::incrementAndGet);
        assertTrue(manager.isRunning());
        assertNotNull(handle);
        Thread.sleep(2500);
        assertTrue(runs.get() >= 1);
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
     * 验证<code>manager.isRunning()</code>为真，<code>handle</code>非空，<code>runs.get() &gt;= 1</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void scheduleAtFixedRateRunsTask() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.scheduleAtFixedRate("view-7", 1, runs::incrementAndGet);

        assertTrue(manager.isRunning());
        assertNotNull(handle);
        Thread.sleep(1500);
        assertTrue(runs.get() >= 1);
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
