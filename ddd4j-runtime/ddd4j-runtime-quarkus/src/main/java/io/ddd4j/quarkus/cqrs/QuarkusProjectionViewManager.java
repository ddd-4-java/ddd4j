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
    private final ViewScheduler scheduler;
    private final ProjectionRunner<?> runner;
    private final Instance<ProjectionView<?>> views;
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

    @Override
    public void start() {
        if (Objects.nonNull(views)) {
            delegate().start();
        }
    }

    @Override
    public void stop() {
        if (Objects.nonNull(delegate)) {
            delegate.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return Objects.nonNull(delegate) && delegate.isRunning();
    }

    @Override
    public void triggerOnce() {
        if (Objects.nonNull(views)) {
            delegate().triggerOnce();
        }
    }

    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return delegate().getProjectionStatus(streamId);
    }
}
