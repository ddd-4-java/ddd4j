/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.data.projection.spring;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Spring 投影视图管理器（{@link ViewManager} SPI 的 Spring 实现）。
 *
 * <p>以 {@code @Component} + {@link SmartLifecycle} 实现 Spring 生命周期适配：
 * 注入所有 {@link ProjectionView} Beans + {@link ProjectionRunner}；
 * {@link #start()} 遍历 views 按 cron 调 {@link ViewScheduler#schedule}，
 * {@link #stop()} 取消全部 handle，{@link #triggerOnce()} 立即执行一次所有 view 的 runOnce。
 * 实现 {@link SmartLifecycle} 使 Spring 容器启动后自动调用 {@link #start()}，
 * 与 Quarkus 版 {@code QuarkusProjectionViewManager} 的 {@code @Observes StartupEvent} 行为对齐。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see SmartLifecycle
 * @see ProjectionRunner
 * @since 2.0.x
 */
@Component
public class SpringProjectionViewManager implements ViewManager, SmartLifecycle {

    /** \u63d0\u4f9b\u5b9e\u9645\u542f\u52a8\u3001\u505c\u6b62\u548c\u89c6\u56fe\u8c03\u5ea6\u7684\u5171\u4eab\u7ba1\u7406\u5668\u3002 */
    private final io.ddd4j.core.cqrs.readmodel.DefaultViewManager<Object> delegate;

    /**
     * 从 Spring 容器装配投影视图管理器。
     *
     * @param scheduler 视图调度器
     * @param runner    投影执行器
     * @param views     可用的投影视图
     */
    @Autowired
    public SpringProjectionViewManager(ViewScheduler scheduler,
                                       ProjectionRunner<?> runner,
                                       Collection<ProjectionView<?>> views) {
        this.delegate = createDelegate(scheduler, runner, views);
    }

    /**
     * \u4ece\u8c03\u5ea6\u5668\u3001\u6267\u884c\u5668\u53ca\u89c6\u56fe\u521b\u5efa\u5171\u4eab\u89c6\u56fe\u7ba1\u7406\u5668\u3002
     * @param scheduler \u8c03\u5ea6\u6295\u5f71\u4efb\u52a1\u7684\u8fd0\u884c\u65f6\u7ec4\u4ef6
     * @param runner \u8c03\u7528\u89c6\u56fe\u5e76\u7ef4\u62a4\u4e8b\u4ef6\u4f4d\u70b9\u7684\u6295\u5f71\u8fd0\u884c\u5668
     * @param views \u5f53\u524d\u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u6295\u5f71\u89c6\u56fe\u96c6\u5408
     * @return \u8d1f\u8d23\u5b9e\u9645\u6295\u5f71\u542f\u505c\u7684\u5171\u4eab\u7ba1\u7406\u5668
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static io.ddd4j.core.cqrs.readmodel.DefaultViewManager<Object> createDelegate(
            ViewScheduler scheduler, ProjectionRunner<?> runner, Collection<ProjectionView<?>> views) {
        return new io.ddd4j.core.cqrs.readmodel.DefaultViewManager<>(scheduler,
                (ProjectionRunner<Object>) runner, (Collection) views);
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>Spring 实现调用 <code>delegate.start()</code>。</p>
     */
    @Override
    public void start() {
        delegate.start();
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>Spring 实现调用 <code>delegate.stop()</code>。</p>
     */
    @Override
    public void stop() {
        delegate.stop();
    }

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * <p>Spring 实现调用 <code>delegate.isRunning()</code>。</p>
     *
     * @return 实际返回<code>delegate.isRunning()</code>
     */
    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    /**
     * \u58f0\u660e Spring \u662f\u5426\u5e94\u968f\u5e94\u7528\u4e0a\u4e0b\u6587\u81ea\u52a8\u542f\u52a8\u89c6\u56fe\u7ba1\u7406\u5668\u3002
     * @return \u662f\u5426\u542f\u7528\u81ea\u52a8\u542f\u52a8
     */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * \u58f0\u660e\u5f53\u524d\u7ec4\u4ef6\u5728 Spring \u751f\u547d\u5468\u671f\u4e2d\u7684\u542f\u505c\u987a\u5e8f\u3002
     * @return \u5f53\u524d\u7ec4\u4ef6\u7684\u751f\u547d\u5468\u671f\u9636\u6bb5
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    /**
     * 请求立即执行指定投影视图的一轮更新。
     *
     * <p>Spring 实现调用 <code>delegate.triggerOnce()</code>。</p>
     */
    @Override
    public void triggerOnce() {
        delegate.triggerOnce();
    }

    /**
     * 汇总当前登记的投影视图及其运行状态。
     *
     * <p>Spring 实现调用 <code>delegate.getProjectionStatus(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 实际返回<code>delegate.getProjectionStatus(streamId)</code>
     */
    @Override
    public io.ddd4j.core.cqrs.readmodel.ProjectionStatus getProjectionStatus(String streamId) {
        return delegate.getProjectionStatus(streamId);
    }
}
