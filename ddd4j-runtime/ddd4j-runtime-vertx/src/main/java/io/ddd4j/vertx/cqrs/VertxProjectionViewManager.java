package io.ddd4j.vertx.cqrs;

import io.ddd4j.core.cqrs.readmodel.*;
import io.vertx.core.Vertx;

import java.util.Collection;
import java.util.Objects;

/**
 * 手动生命周期适配器，注册/回滚/状态统一委派 core；stop 后可 restart。
 * triggerOnce 保留同步契约，阻塞存储调用方须使用 runtime.executeBlocking。
 */
public class VertxProjectionViewManager implements ViewManager, AutoCloseable {
    private final DefaultViewManager<Object> delegate;
    private final VertxProjectionScheduler ownedScheduler;
    private volatile boolean closed;

    /**
     * 借用外部调度器，关闭时不关闭其资源。
     *
     * @param scheduler 外部调度器
     * @param runner    共享执行器
     * @param views     视图快照
     */
    public VertxProjectionViewManager(ViewScheduler scheduler, ProjectionRunner<?> runner,
                                      Collection<ProjectionView<?>> views) {
        this(scheduler, runner, views, null);
    }

    @SuppressWarnings("unchecked")
    private VertxProjectionViewManager(ViewScheduler scheduler, ProjectionRunner<?> runner,
                                       Collection<ProjectionView<?>> views, VertxProjectionScheduler owned) {
        delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner,
                (Collection<ProjectionView<Object>>) (Collection<?>) views);
        ownedScheduler = owned;
    }

    /**
     * 创建拥有自身 timer 的管理器，不自动启动。
     *
     * @param vertx  调用方实例
     * @param views  视图集合
     * @param runner 共享执行器
     * @return 未启动管理器
     */
    public static VertxProjectionViewManager create(Vertx vertx, Collection<ProjectionView<?>> views,
                                                    ProjectionRunner<?> runner) {
        VertxProjectionScheduler scheduler = new VertxProjectionScheduler(vertx);
        try {
            return new VertxProjectionViewManager(scheduler, runner, views, scheduler);
        } catch (RuntimeException failure) {
            scheduler.close();
            throw failure;
        }
    }

    @Override
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("Projection manager is closed");
        }
        delegate.start();
    }

    @Override
    public synchronized void stop() {
        delegate.stop();
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public synchronized void close() {
        closed = true;
        try {
            delegate.stop();
        } finally {
            if (Objects.nonNull(ownedScheduler)) {
                ownedScheduler.close();
            }
        }
    }

    @Override
    public void triggerOnce() {
        if (closed) {
            throw new IllegalStateException("Projection manager is closed");
        }
        delegate.triggerOnce();
    }

    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return delegate.getProjectionStatus(streamId);
    }
}
