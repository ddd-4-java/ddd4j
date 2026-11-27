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
    /** \u8d1f\u8d23\u5b89\u6392\u6295\u5f71\u89e6\u53d1\u548c\u53d6\u6d88\u7684\u8c03\u5ea6\u5668\u3002 */
    private final ViewScheduler scheduler;
    /** \u5b9e\u9645\u8bfb\u53d6\u5e76\u5e94\u7528\u6295\u5f71\u4e8b\u4ef6\u7684\u6267\u884c\u5668\u3002 */
    private final ProjectionRunner<?> runner;
    /** \u7531\u5f53\u524d\u8fd0\u884c\u73af\u5883\u7ba1\u7406\u548c\u8c03\u5ea6\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    private final Instance<ProjectionView<?>> views;
    /** \u6309\u9700\u88c5\u914d\u5e76\u590d\u7528\u7684\u6295\u5f71\u89c6\u56fe\u7ba1\u7406\u5668\u3002 */
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
     * \u83b7\u53d6\u5df2\u521d\u59cb\u5316\u7684\u5171\u4eab\u89c6\u56fe\u7ba1\u7406\u5668\uff0c\u5fc5\u8981\u65f6\u5b8c\u6210\u88c5\u914d\u3002
     * @return \u7528\u4e8e\u5b9e\u9645\u6295\u5f71\u64cd\u4f5c\u7684\u5171\u4eab\u7ba1\u7406\u5668
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

    /** {@inheritDoc} */
    @Override
    public void start() {
        if (Objects.nonNull(views)) {
            delegate().start();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        if (Objects.nonNull(delegate)) {
            delegate.stop();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRunning() {
        return Objects.nonNull(delegate) && delegate.isRunning();
    }

    /** {@inheritDoc} */
    @Override
    public void triggerOnce() {
        if (Objects.nonNull(views)) {
            delegate().triggerOnce();
        }
    }

    /** {@inheritDoc} */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return delegate().getProjectionStatus(streamId);
    }
}
