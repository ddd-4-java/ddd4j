package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler.ViewScheduleHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiceViewManagerTest {

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

    @Test
    void scheduleWithEmptyCronThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.schedule("view-2", "", () -> {});
        });
    }

    @Test
    void scheduleWithInvalidCronThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.schedule("view-3", "0/not-a-number", () -> {});
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
            manager.schedule("view-6", "0 0 2 * * ?", () -> {});
        });
    }

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

    @Test
    void scheduleAtFixedRateWithInvalidIntervalThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.scheduleAtFixedRate("view-8", 0, () -> {});
        });
        assertThrows(IllegalArgumentException.class, () -> {
            manager.scheduleAtFixedRate("view-9", -1, () -> {});
        });
    }
}
