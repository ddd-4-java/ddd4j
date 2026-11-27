package io.ddd4j.helidon.cqrs;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import io.ddd4j.core.cqrs.readmodel.*;

import java.util.Collection;

/**
 * Helidon 视图适配器；手动 SE 与 MP 生命周期均委派共享管理器。
 */
@Singleton
public class HelidonProjectionViewManager implements ViewManager {
    /**
     * 执行 Helidon 投影视图生命周期的共享管理器。
     */
    private final DefaultViewManager<Object> delegate;

    /**
     * 创建管理器，不启动任务也不取得用户调度器所有权。
     *
     * @param scheduler 调度器
     * @param runner    共享投影执行器
     * @param views     视图快照
     */
    @SuppressWarnings("unchecked")
    @Inject
    public HelidonProjectionViewManager(ViewScheduler scheduler, ProjectionRunner<?> runner,
                                        Collection<ProjectionView<?>> views) {
        delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner,
                (Collection<ProjectionView<Object>>) (Collection<?>) views);
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>Helidon 实现调用 <code>delegate.start()</code>。</p>
     */
    @Override
    public void start() {
        delegate.start();
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>Helidon 实现调用 <code>delegate.stop()</code>。</p>
     */
    @Override
    public void stop() {
        delegate.stop();
    }

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * <p>Helidon 实现调用 <code>delegate.isRunning()</code>。</p>
     *
     * @return 实际返回<code>delegate.isRunning()</code>
     */
    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    /**
     * 请求立即执行指定投影视图的一轮更新。
     *
     * <p>Helidon 实现调用 <code>delegate.triggerOnce()</code>。</p>
     */
    @Override
    public void triggerOnce() {
        delegate.triggerOnce();
    }

    /**
     * 汇总当前登记的投影视图及其运行状态。
     *
     * <p>Helidon 实现调用 <code>delegate.getProjectionStatus(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 实际返回<code>delegate.getProjectionStatus(streamId)</code>
     */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return delegate.getProjectionStatus(streamId);
    }
}
