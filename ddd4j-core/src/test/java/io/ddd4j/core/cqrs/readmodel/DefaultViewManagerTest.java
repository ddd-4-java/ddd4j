package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通过 <code>tasks.add()</code>、<code>handles.add()</code>、<code>service.updateProjectionPosition()</code> 验证 <code>DefaultViewManager</code> 的运行行为。
 */
class DefaultViewManagerTest {
    /**
     * 验证<code>name</code> 等于 <code>"orders"</code>，<code>cron</code> 等于 <code>"0/5 * * * * ?"</code>，<code>service.readProjectionPosition("orders")</code> 等于 <code>30</code>。
     */
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
                (id, from, size, types) -> new EventChunk<>(java.util.Arrays.asList("event"), from + 10));
        DefaultViewManager<String> manager = new DefaultViewManager<>(scheduler, runner,
                java.util.Arrays.asList(new ProjectionRunnerTest.TestView("orders", null, 100, java.util.Arrays.asList("created"))));
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

    /**
     * 验证<code>manager.isRunning()</code>为假，<code>handles.get(0).isActive()</code>为假，<code>manager.isRunning()</code>为真。
     */
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
        DefaultViewManager<String> manager = new DefaultViewManager<>(scheduler, runner, java.util.Arrays.asList(
                new ProjectionRunnerTest.TestView("first", null, 100, java.util.Collections.emptyList()),
                new ProjectionRunnerTest.TestView("second", null, 100, java.util.Collections.emptyList())));
        assertThatThrownBy(manager::start).hasMessage("schedule failed");
        assertThat(manager.isRunning()).isFalse();
        assertThat(handles.get(0).isActive()).isFalse();
        manager.start();
        assertThat(manager.isRunning()).isTrue();
        manager.stop();
        assertThat(handles).allMatch(handle -> !handle.isActive());
    }

    /**
     * 验证<code>other.isActive()</code>为假。
     */
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
        DefaultViewManager<String> manager = new DefaultViewManager<>(scheduler, runner, java.util.Arrays.asList(
                new ProjectionRunnerTest.TestView("first", null, 100, java.util.Collections.emptyList()),
                new ProjectionRunnerTest.TestView("second", null, 100, java.util.Collections.emptyList())));
        manager.start();
        assertThatThrownBy(manager::stop).hasMessage("cancel failed");
        assertThat(other.isActive()).isFalse();
        assertThatThrownBy(manager::start).hasMessageContaining("Cancel remaining");
        manager.stop();
        manager.start();
        manager.stop();
    }

    /**
     * 验证<code>manager.isRunning()</code>为假，<code>first.isActive()</code>为假，<code>manager.isRunning()</code>为真。
     */
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

    /**
     * 验证<code>manager.isRunning()</code>为假，<code>other.isActive()</code>为假。
     */
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

    /**
     * 实现 <code>ViewScheduleHandle</code>，供测试调用 <code>cancel()</code>、<code>isActive()</code> 并观察结果。
     */
    private static final class Handle implements ViewScheduler.ViewScheduleHandle {
        /**
         * 记录测试观察到的任务取消调用次数。
         */
        private int cancels;

        /**
         * 撤销当前任务句柄允许撤销的操作。
         */
        @Override
        public void cancel() {
            cancels++;
        }

        /**
         * 检查当前句柄是否仍处于有效状态。
         *
         * @return 实际返回<code>cancels == 0</code>，作为本次判断结果
         */
        @Override
        public boolean isActive() {
            return cancels == 0;
        }
    }
}
