package io.ddd4j.helidon.cqrs;

import io.ddd4j.core.cqrs.readmodel.*;

import java.util.Collection;

/**
 * Helidon 视图适配器；手动 SE 与 MP 生命周期均委派共享管理器。
 */
public class HelidonProjectionViewManager implements ViewManager {
    /** \u6267\u884c Helidon \u6295\u5f71\u89c6\u56fe\u751f\u547d\u5468\u671f\u7684\u5171\u4eab\u7ba1\u7406\u5668\u3002 */
    private final DefaultViewManager<Object> delegate;

    /**
     * 创建管理器，不启动任务也不取得用户调度器所有权。
     *
     * @param scheduler 调度器
     * @param runner    共享投影执行器
     * @param views     视图快照
     */
    @SuppressWarnings("unchecked")
    public HelidonProjectionViewManager(ViewScheduler scheduler, ProjectionRunner<?> runner,
                                        Collection<ProjectionView<?>> views) {
        delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner,
                (Collection<ProjectionView<Object>>) (Collection<?>) views);
    }

    /** {@inheritDoc} */
    @Override
    public void start() {
        delegate.start();
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        delegate.stop();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    /** {@inheritDoc} */
    @Override
    public void triggerOnce() {
        delegate.triggerOnce();
    }

    /** {@inheritDoc} */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return delegate.getProjectionStatus(streamId);
    }
}
