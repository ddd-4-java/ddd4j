package io.ddd4j.core.cqrs.readmodel;

import java.util.*;

/**
 * 共享视图生命周期：提取轻量平台重复的注册、取消句柄和手动触发流程。
 * 平台仍拥有线程池、容器钩子和事务；停止只取消后续调度，不中断已运行的业务。
 */
public final class DefaultViewManager<E> implements ViewManager {
    /**
     * 负责定时触发各个投影视图的调度器。
     */
    private final ViewScheduler scheduler;
    /**
     * 实际读取、应用事件并推进位置的投影运行器。
     */
    private final ProjectionRunner<E> runner;
    /**
     * 当前已经注册的投影视图。
     */
    private final List<? extends ProjectionView<E>> views;
    /**
     * 用于取消各视图调度的句柄。
     */
    private final List<ViewScheduler.ViewScheduleHandle> handles = new ArrayList<>();
    /**
     * 视图管理器是否已启动调度。
     */
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
        this.views = java.util.Collections.unmodifiableList(views.stream().map(java.util.Objects::requireNonNull).collect(java.util.stream.Collectors.toList()));
        Set<String> names = new HashSet<>();
        for (ProjectionView<E> view : this.views) {
            if (!names.add(view.getName())) {
                throw new IllegalArgumentException("Duplicate view name: " + view.getName());
            }
        }
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>此实现调用 <code>handles.isEmpty()</code>。</p>
     */
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

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>此实现调用 <code>handles.iterator()</code>。</p>
     */
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

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * @return 实际返回<code>running</code>，作为本次判断结果
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 请求立即执行指定投影视图的一轮更新。
     *
     * <p>此实现调用 <code>runner.runAll(views)</code>。</p>
     */
    @Override
    public void triggerOnce() {
        runner.runAll(views);
    }

    /**
     * 汇总当前登记的投影视图及其运行状态。
     *
     * <p>此实现调用 <code>runner.getProjectionStatus(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 投影状态快照
     */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        ProjectionStatus execution = runner.getProjectionStatus(streamId);
        return new ProjectionStatus(streamId, execution.nextEventNumber(), running,
                execution.lastRunAt(), execution.lastEventCount(), execution.lastError());
    }
}
