package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** \u901a\u8fc7 <code>tasks.add()</code>\u3001<code>handles.add()</code>\u3001<code>service.updateProjectionPosition()</code> \u9a8c\u8bc1 <code>DefaultViewManager</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class DefaultViewManagerTest {
    /** \u9a8c\u8bc1<code>name</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>cron</code> \u7b49\u4e8e <code>"0/5 * * * * ?"</code>\uff0c<code>service.readProjectionPosition("orders")</code> \u7b49\u4e8e <code>30</code>\u3002 */
    @Test
    void repeatedStartStopKeepsOneHandleAndCanRestartFromSavedPosition() {
        List<Handle> handles = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        ViewScheduler scheduler = (name, cron, task) -> {
            assertThat(name).isEqualTo("orders");
            assertThat(cron).isEqualTo("0/5 * * * * ?");
            tasks.add(task);
            Handle handle = new Handle();
            handles.add(handle);
            return handle;
        };
        ProjectionService service = new DefaultProjectionService(new InMemoryProjectionPositionRepository());
        service.updateProjectionPosition("orders", 10);
        ProjectionRunner<String> runner = new ProjectionRunner<>(service,
                (id, from, size, types) -> new EventChunk<>(List.of("event"), from + 10));
        DefaultViewManager<String> manager = new DefaultViewManager<>(scheduler, runner,
                List.of(new ProjectionRunnerTest.TestView("orders", null, 100, List.of("created"))));
        manager.start();
        manager.start();
        assertThat(handles).hasSize(1);
        tasks.get(0).run();
        assertThat(manager.getProjectionStatus("orders").nextEventNumber()).isEqualTo(20);
        assertThat(manager.getProjectionStatus("orders").running()).isTrue();
        manager.stop();
        manager.stop();
        assertThat(handles.get(0).cancels).isEqualTo(1);
        assertThat(manager.isRunning()).isFalse();
        manager.start();
        assertThat(handles).hasSize(2);
        manager.triggerOnce();
        assertThat(service.readProjectionPosition("orders")).isEqualTo(30);
        manager.stop();
    }

    /** \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u5047\uff0c<code>handles.get(0).isActive()</code>\u4e3a\u5047\uff0c<code>manager.isRunning()</code>\u4e3a\u771f\u3002 */
    @Test
    void partialStartFailureCancelsCreatedHandlesAndAllowsRetry() {
        List<Handle> handles = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean(true);
        ViewScheduler scheduler = (name, cron, task) -> {
            if ("second".equals(name) && fail.getAndSet(false)) {
                throw new IllegalStateException("schedule failed");
            }
            Handle handle = new Handle();
            handles.add(handle);
            return handle;
        };
        ProjectionRunner<String> runner = new ProjectionRunner<>(
                new DefaultProjectionService(new InMemoryProjectionPositionRepository()),
                (id, from, size, types) -> EventChunk.empty(from));
        DefaultViewManager<String> manager = new DefaultViewManager<>(scheduler, runner, List.of(
                new ProjectionRunnerTest.TestView("first", null, 100, List.of()),
                new ProjectionRunnerTest.TestView("second", null, 100, List.of())));
        assertThatThrownBy(manager::start).hasMessage("schedule failed");
        assertThat(manager.isRunning()).isFalse();
        assertThat(handles.get(0).isActive()).isFalse();
        manager.start();
        assertThat(manager.isRunning()).isTrue();
        manager.stop();
        assertThat(handles).allMatch(handle -> !handle.isActive());
    }

    /** \u9a8c\u8bc1<code>other.isActive()</code>\u4e3a\u5047\u3002 */
    @Test
    void cancellationFailureStillCancelsOtherHandlesAndIsRetriedBeforeRestart() {
        AtomicBoolean failCancel = new AtomicBoolean(true);
        Handle other = new Handle();
        ViewScheduler scheduler = (name, cron, task) -> "first".equals(name)
                ? new ViewScheduler.ViewScheduleHandle() {
            @Override
            public void cancel() {
                if (failCancel.getAndSet(false)) {
                    throw new IllegalStateException("cancel failed");
                }
            }

            @Override
            public boolean isActive() {
                return failCancel.get();
            }
        } : other;
        ProjectionRunner<String> runner = new ProjectionRunner<>(
                new DefaultProjectionService(new InMemoryProjectionPositionRepository()),
                (id, from, size, types) -> EventChunk.empty(from));
        DefaultViewManager<String> manager = new DefaultViewManager<>(scheduler, runner, List.of(
                new ProjectionRunnerTest.TestView("first", null, 100, List.of()),
                new ProjectionRunnerTest.TestView("second", null, 100, List.of())));
        manager.start();
        assertThatThrownBy(manager::stop).hasMessage("cancel failed");
        assertThat(other.isActive()).isFalse();
        assertThatThrownBy(manager::start).hasMessageContaining("Cancel remaining");
        manager.stop();
        manager.start();
        manager.stop();
    }

    /** \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u5047\uff0c<code>first.isActive()</code>\u4e3a\u5047\uff0c<code>manager.isRunning()</code>\u4e3a\u771f\u3002 */
    @Test
    void partialStartErrorCancelsCreatedHandlesAndRethrowsOriginal() {
        LinkageError unavailableScheduler = new LinkageError("scheduler unavailable");
        AtomicBoolean fail = new AtomicBoolean(true);
        Handle first = new Handle();
        ViewScheduler scheduler = (name, cron, task) -> {
            if ("second".equals(name) && fail.getAndSet(false)) {
                throw unavailableScheduler;
            }
            return "first".equals(name) && first.isActive() ? first : new Handle();
        };
        ProjectionRunner<String> runner = new ProjectionRunner<>(
                new DefaultProjectionService(new InMemoryProjectionPositionRepository()),
                (id, from, size, types) -> EventChunk.empty(from));
        DefaultViewManager<String> manager = new DefaultViewManager<>(scheduler, runner, Arrays.asList(
                new ProjectionRunnerTest.TestView("first", null, 100, Collections.emptyList()),
                new ProjectionRunnerTest.TestView("second", null, 100, Collections.emptyList())));

        assertThatThrownBy(manager::start).isSameAs(unavailableScheduler);
        assertThat(manager.isRunning()).isFalse();
        assertThat(first.isActive()).isFalse();
        manager.start();
        assertThat(manager.isRunning()).isTrue();
        manager.stop();
    }

    /** \u9a8c\u8bc1<code>manager.isRunning()</code>\u4e3a\u5047\uff0c<code>other.isActive()</code>\u4e3a\u5047\u3002 */
    @Test
    void cancellationErrorStillCancelsOtherHandlesAndCanBeRetried() {
        LinkageError unavailableScheduler = new LinkageError("cancel unavailable");
        AtomicBoolean failCancel = new AtomicBoolean(true);
        Handle other = new Handle();
        ViewScheduler.ViewScheduleHandle first = new ViewScheduler.ViewScheduleHandle() {
            @Override
            public void cancel() {
                if (failCancel.getAndSet(false)) {
                    throw unavailableScheduler;
                }
            }

            @Override
            public boolean isActive() {
                return failCancel.get();
            }
        };
        ViewScheduler scheduler = (name, cron, task) -> "first".equals(name) ? first : other;
        ProjectionRunner<String> runner = new ProjectionRunner<>(
                new DefaultProjectionService(new InMemoryProjectionPositionRepository()),
                (id, from, size, types) -> EventChunk.empty(from));
        DefaultViewManager<String> manager = new DefaultViewManager<>(scheduler, runner, Arrays.asList(
                new ProjectionRunnerTest.TestView("first", null, 100, Collections.emptyList()),
                new ProjectionRunnerTest.TestView("second", null, 100, Collections.emptyList())));

        manager.start();
        assertThatThrownBy(manager::stop).isSameAs(unavailableScheduler);
        assertThat(manager.isRunning()).isFalse();
        assertThat(other.isActive()).isFalse();
        assertThatThrownBy(manager::start).hasMessageContaining("Cancel remaining");
        manager.stop();
        manager.start();
        manager.stop();
    }

    /** \u5b9e\u73b0 <code>ViewScheduleHandle</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>cancel()</code>\u3001<code>isActive()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class Handle implements ViewScheduler.ViewScheduleHandle {
        /** \u8bb0\u5f55\u6d4b\u8bd5\u89c2\u5bdf\u5230\u7684\u4efb\u52a1\u53d6\u6d88\u8c03\u7528\u6b21\u6570\u3002 */
        private int cancels;

        /** {@inheritDoc} */
        @Override
        public void cancel() {
            cancels++;
        }

        /** {@inheritDoc} */
        @Override
        public boolean isActive() {
            return cancels == 0;
        }
    }
}
