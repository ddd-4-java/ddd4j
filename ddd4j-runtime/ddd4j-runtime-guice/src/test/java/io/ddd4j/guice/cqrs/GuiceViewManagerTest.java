package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler.ViewScheduleHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void scheduleWithEmptyCronDefaultsToMinute() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.schedule("view-2", "", runs::incrementAndGet);
        Thread.sleep(100);
        handle.cancel();
        assertEquals(0, runs.get());
    }

    @Test
    void scheduleWithInvalidCronFallsBack() {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.schedule("view-3", "0/not-a-number", runs::incrementAndGet);
        assertNotNull(handle);
        handle.cancel();
    }

    @Test
    void scheduleWithPlainCronDefaultsToMinute() {
        AtomicInteger runs = new AtomicInteger();
        ViewScheduleHandle handle = manager.schedule("view-4", "0 * * * *", runs::incrementAndGet);
        assertNotNull(handle);
        handle.cancel();
    }
}
