package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultViewManagerTest {
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

    private static final class Handle implements ViewScheduler.ViewScheduleHandle {
        private int cancels;
        @Override
        public void cancel() {
            cancels++;
        }
        @Override
        public boolean isActive() {
            return cancels == 0;
        }
    }
}
