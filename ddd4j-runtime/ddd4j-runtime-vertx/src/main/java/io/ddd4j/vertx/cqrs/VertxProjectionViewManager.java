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
    /**
     * 管理 Vert.x 投影视图和任务句柄的共享实现。
     */
    private final DefaultViewManager<Object> delegate;
    /**
     * 当前运行实例拥有并负责关闭的调度器。
     */
    private final VertxProjectionScheduler ownedScheduler;
    /**
     * 当前实例是否已关闭。
     */
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

    /**
     * 以给定调度器和执行器装配 Vert.x 投影，并记录自有资源。
     *
     * @param scheduler 调度投影任务的运行时组件
     * @param runner    调用视图并维护事件位点的投影运行器
     * @param views     当前运行时注册的投影视图集合
     * @param owned     当前管理器退出时负责关闭的自有调度器
     */
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

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>满足 <code>closed</code> 时抛出 <code>IllegalStateException</code>。 Vert.x 实现调用 <code>delegate.start()</code>。</p>
     */
    @Override
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("Projection manager is closed");
        }
        delegate.start();
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>Vert.x 实现调用 <code>delegate.stop()</code>。</p>
     */
    @Override
    public synchronized void stop() {
        delegate.stop();
    }

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * <p>Vert.x 实现调用 <code>delegate.isRunning()</code>。</p>
     *
     * @return 实际返回<code>delegate.isRunning()</code>
     */
    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>Vert.x 实现调用 <code>delegate.stop()</code>。</p>
     */
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

    /**
     * 请求立即执行指定投影视图的一轮更新。
     *
     * <p>满足 <code>closed</code> 时抛出 <code>IllegalStateException</code>。 Vert.x 实现调用 <code>delegate.triggerOnce()</code>。</p>
     */
    @Override
    public void triggerOnce() {
        if (closed) {
            throw new IllegalStateException("Projection manager is closed");
        }
        delegate.triggerOnce();
    }

    /**
     * 汇总当前登记的投影视图及其运行状态。
     *
     * <p>Vert.x 实现调用 <code>delegate.getProjectionStatus(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 实际返回<code>delegate.getProjectionStatus(streamId)</code>
     */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return delegate.getProjectionStatus(streamId);
    }
}
