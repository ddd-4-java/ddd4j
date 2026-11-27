package io.ddd4j.core.cqrs.readmodel;

import java.util.*;

/**
 * 共享视图生命周期：提取轻量平台重复的注册、取消句柄和手动触发流程。
 * 平台仍拥有线程池、容器钩子和事务；停止只取消后续调度，不中断已运行的业务。
 */
public final class DefaultViewManager<E> implements ViewManager {
    /** \u8d1f\u8d23\u5b9a\u65f6\u89e6\u53d1\u5404\u4e2a\u6295\u5f71\u89c6\u56fe\u7684\u8c03\u5ea6\u5668\u3002 */
    private final ViewScheduler scheduler;
    /** \u5b9e\u9645\u8bfb\u53d6\u3001\u5e94\u7528\u4e8b\u4ef6\u5e76\u63a8\u8fdb\u4f4d\u7f6e\u7684\u6295\u5f71\u8fd0\u884c\u5668\u3002 */
    private final ProjectionRunner<E> runner;
    /** \u5f53\u524d\u5df2\u7ecf\u6ce8\u518c\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    private final List<? extends ProjectionView<E>> views;
    /** \u7528\u4e8e\u53d6\u6d88\u5404\u89c6\u56fe\u8c03\u5ea6\u7684\u53e5\u67c4\u3002 */
    private final List<ViewScheduler.ViewScheduleHandle> handles = new ArrayList<>();
    /** \u89c6\u56fe\u7ba1\u7406\u5668\u662f\u5426\u5df2\u542f\u52a8\u8c03\u5ea6\u3002 */
    private volatile boolean running;

    /**
     * 创建共享管理器，不启动任务。
     *
     * @param scheduler 平台提供的调度器
     * @param runner    共享运行器
     * @param views     独立命名的视图集合
     */
    public DefaultViewManager(ViewScheduler scheduler, ProjectionRunner<E> runner,
                              Collection<? extends ProjectionView<E>> views) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = List.copyOf(views);
        Set<String> names = new HashSet<>();
        for (ProjectionView<E> view : this.views) {
            if (!names.add(view.getName())) {
                throw new IllegalArgumentException("Duplicate view name: " + view.getName());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (!handles.isEmpty()) {
            throw new IllegalStateException("Cancel remaining view handles before restart");
        }
        try {
            for (ProjectionView<E> view : views) {
                handles.add(Objects.requireNonNull(scheduler.schedule(view.getName(), view.getCron(),
                        () -> runner.runOnce(view)), "scheduler must return a handle"));
            }
            running = true;
        } catch (RuntimeException | Error failure) {
            try {
                stop();
            } catch (RuntimeException | Error cancellationFailure) {
                if (failure != cancellationFailure) {
                    failure.addSuppressed(cancellationFailure);
                }
            }
            throw failure;
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void stop() {
        running = false;
        Throwable firstFailure = null;
        Iterator<ViewScheduler.ViewScheduleHandle> iterator = handles.iterator();
        while (iterator.hasNext()) {
            try {
                iterator.next().cancel();
                iterator.remove();
            } catch (RuntimeException | Error failure) {
                if (Objects.isNull(firstFailure)) {
                    firstFailure = failure;
                } else if (firstFailure != failure) {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        if (firstFailure instanceof RuntimeException) {
            throw (RuntimeException) firstFailure;
        }
        if (firstFailure instanceof Error) {
            throw (Error) firstFailure;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRunning() {
        return running;
    }

    /** {@inheritDoc} */
    @Override
    public void triggerOnce() {
        runner.runAll(views);
    }

    /** {@inheritDoc} */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        ProjectionStatus execution = runner.getProjectionStatus(streamId);
        return new ProjectionStatus(streamId, execution.nextEventNumber(), running,
                execution.lastRunAt(), execution.lastEventCount(), execution.lastError());
    }
}
