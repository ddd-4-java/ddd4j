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
    /** \u7ba1\u7406 Vert.x \u6295\u5f71\u89c6\u56fe\u548c\u4efb\u52a1\u53e5\u67c4\u7684\u5171\u4eab\u5b9e\u73b0\u3002 */
    private final DefaultViewManager<Object> delegate;
    /** \u5f53\u524d\u8fd0\u884c\u5b9e\u4f8b\u62e5\u6709\u5e76\u8d1f\u8d23\u5173\u95ed\u7684\u8c03\u5ea6\u5668\u3002 */
    private final VertxProjectionScheduler ownedScheduler;
    /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
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
     * \u4ee5\u7ed9\u5b9a\u8c03\u5ea6\u5668\u548c\u6267\u884c\u5668\u88c5\u914d Vert.x \u6295\u5f71\uff0c\u5e76\u8bb0\u5f55\u81ea\u6709\u8d44\u6e90\u3002
     * @param scheduler \u8c03\u5ea6\u6295\u5f71\u4efb\u52a1\u7684\u8fd0\u884c\u65f6\u7ec4\u4ef6
     * @param runner \u8c03\u7528\u89c6\u56fe\u5e76\u7ef4\u62a4\u4e8b\u4ef6\u4f4d\u70b9\u7684\u6295\u5f71\u8fd0\u884c\u5668
     * @param views \u5f53\u524d\u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u6295\u5f71\u89c6\u56fe\u96c6\u5408
     * @param owned \u5f53\u524d\u7ba1\u7406\u5668\u9000\u51fa\u65f6\u8d1f\u8d23\u5173\u95ed\u7684\u81ea\u6709\u8c03\u5ea6\u5668
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
