package io.ddd4j.helidon.cqrs;

import io.ddd4j.core.cqrs.readmodel.DefaultViewManager;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import java.util.Collection;

/** Helidon 视图适配器；手动 SE 与 MP 生命周期均委派共享管理器。 */
public class HelidonProjectionViewManager implements ViewManager {
    private final DefaultViewManager<Object> delegate;
    /** 创建管理器，不启动任务也不取得用户调度器所有权。
     * @param scheduler 调度器
     * @param runner 共享投影执行器
     * @param views 视图快照
     */
    @SuppressWarnings("unchecked")
    public HelidonProjectionViewManager(ViewScheduler scheduler, ProjectionRunner<?> runner,
                                         Collection<ProjectionView<?>> views) {
        delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner,
                (Collection<ProjectionView<Object>>) (Collection<?>) views);
    }
    @Override
    public void start() { delegate.start(); }
    @Override
    public void stop() { delegate.stop(); }
    @Override
    public boolean isRunning() { return delegate.isRunning(); }
    @Override
    public void triggerOnce() { delegate.triggerOnce(); }
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) { return delegate.getProjectionStatus(streamId); }
}
