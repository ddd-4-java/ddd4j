package io.ddd4j.dropwizard.cqrs;

import io.ddd4j.core.cqrs.readmodel.*;
import io.dropwizard.core.setup.Environment;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Dropwizard 视图生命周期；共享管理器持有句柄，平台仅管理自有调度资源。
 */
public class DropwizardProjectionViewManager implements ViewManager, AutoCloseable {
    private final DefaultViewManager<Object> delegate;
    private final Environment environment;
    private final ProjectionRunner<?> runner;
    private final Collection<ProjectionView<?>> views;
    private DropwizardProjectionScheduler ownedScheduler;

    /**
     * 使用借用调度器，不取得调度器、reader 或仓储所有权。
     *
     * @param scheduler 调用方调度器
     * @param runner    共享运行器
     * @param views     视图快照
     */
    @SuppressWarnings("unchecked")
    public DropwizardProjectionViewManager(ViewScheduler scheduler, ProjectionRunner<?> runner,
                                           Collection<ProjectionView<?>> views) {
        this.environment = null;
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = List.copyOf(views);
        this.delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner,
                (Collection<ProjectionView<Object>>) (Collection<?>) this.views);
    }

    /**
     * 创建自有调度器的手动管理器，线程延迟到 start 创建。
     *
     * @param environment 原生环境
     * @param runner      共享运行器
     * @param views       视图快照
     */
    @SuppressWarnings("unchecked")
    protected DropwizardProjectionViewManager(Environment environment, ProjectionRunner<?> runner,
                                              Collection<ProjectionView<?>> views) {
        this.environment = Objects.requireNonNull(environment, "env must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = List.copyOf(views);
        this.delegate = new DefaultViewManager<>((name, cron, task) -> ownedScheduler.schedule(name, cron, task),
                (ProjectionRunner<Object>) runner,
                (Collection<ProjectionView<Object>>) (Collection<?>) this.views);
    }

    /**
     * 保留静态手动工厂；不会自行注册 Managed 或自动启动。
     *
     * @param env    原生环境锚点
     * @param views  视图快照
     * @param runner 共享运行器
     * @return 未启动管理器
     */
    public static DropwizardProjectionViewManager create(Environment env,
                                                         Collection<ProjectionView<?>> views, ProjectionRunner<?> runner) {
        return new DropwizardProjectionViewManager(env, runner, views);
    }

    @Override
    public synchronized void start() {
        if (delegate.isRunning()) {
            return;
        }
        if (Objects.nonNull(environment) && Objects.isNull(ownedScheduler)) {
            ownedScheduler = DropwizardProjectionScheduler.create(environment, views, runner);
        }
        try {
            delegate.start();
        } catch (RuntimeException | Error failure) {
            try {
                stop();
            } catch (RuntimeException | Error cleanup) {
                if (failure != cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    @Override
    public synchronized void stop() {
        try {
            delegate.stop();
        } finally {
            if (Objects.nonNull(ownedScheduler)) {
                // 仅成功等待退出后丢弃旧池；超时保留引用，以便后续 stop 重试。
                ownedScheduler.shutdown();
                ownedScheduler = null;
            }
        }
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public void triggerOnce() {
        delegate.triggerOnce();
    }

    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return delegate.getProjectionStatus(streamId);
    }

    @Override
    public void close() {
        stop();
    }
}
