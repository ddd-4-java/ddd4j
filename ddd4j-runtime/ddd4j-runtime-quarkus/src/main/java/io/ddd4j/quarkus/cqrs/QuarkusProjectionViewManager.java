package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.*;
import jakarta.enterprise.inject.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 旧 CDI 视图入口的共享生命周期装配；无独立 Bean 或执行内核。
 */
public class QuarkusProjectionViewManager implements ViewManager {
    /**
     * 负责安排投影触发和取消的调度器。
     */
    private final ViewScheduler scheduler;
    /**
     * 实际读取并应用投影事件的执行器。
     */
    private final ProjectionRunner<?> runner;
    /**
     * 由当前运行环境管理和调度的投影视图。
     */
    private final Instance<ProjectionView<?>> views;
    /**
     * 按需装配并复用的投影视图管理器。
     */
    private DefaultViewManager<Object> delegate;

    /**
     * 从旧 CDI 注入点装配；视图仍于首次使用时发现。
     *
     * @param scheduler 原生视图调度器
     * @param runner    共享运行器
     * @param views     视图发现入口
     */
    public QuarkusProjectionViewManager(ViewScheduler scheduler, ProjectionRunner<?> runner,
                                        Instance<ProjectionView<?>> views) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = Objects.requireNonNull(views, "views must not be null");
    }

    /**
     * 供旧 ArC 代理构造使用。
     */
    protected QuarkusProjectionViewManager() {
        scheduler = null;
        runner = null;
        views = null;
    }

    /**
     * 获取已初始化的共享视图管理器，必要时完成装配。
     *
     * @return 用于实际投影操作的共享管理器
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private synchronized DefaultViewManager<Object> delegate() {
        if (Objects.isNull(delegate)) {
            List<ProjectionView<?>> snapshot = new ArrayList<>();
            for (ProjectionView<?> view : views) {
                snapshot.add(view);
            }
            delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner, (List) snapshot);
        }
        return delegate;
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>Quarkus 实现调用 <code>delegate().start()</code>。</p>
     */
    @Override
    public void start() {
        if (Objects.nonNull(views)) {
            delegate().start();
        }
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>Quarkus 实现调用 <code>delegate.stop()</code>。</p>
     */
    @Override
    public void stop() {
        if (Objects.nonNull(delegate)) {
            delegate.stop();
        }
    }

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * <p>Quarkus 实现调用 <code>delegate.isRunning()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(delegate) &amp;&amp; delegate.isRunning()</code>
     */
    @Override
    public boolean isRunning() {
        return Objects.nonNull(delegate) && delegate.isRunning();
    }

    /**
     * 请求立即执行指定投影视图的一轮更新。
     *
     * <p>Quarkus 实现调用 <code>delegate().triggerOnce()</code>。</p>
     */
    @Override
    public void triggerOnce() {
        if (Objects.nonNull(views)) {
            delegate().triggerOnce();
        }
    }

    /**
     * 汇总当前登记的投影视图及其运行状态。
     *
     * <p>Quarkus 实现调用 <code>delegate().getProjectionStatus(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 实际返回<code>delegate().getProjectionStatus(streamId)</code>
     */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return delegate().getProjectionStatus(streamId);
    }
}
