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
package io.ddd4j.spring.projection;

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

    /**
     * 提供实际启动、停止和视图调度的共享管理器。
     */
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
     * 从调度器、执行器及视图创建共享视图管理器。
     *
     * @param scheduler 调度投影任务的运行时组件
     * @param runner    调用视图并维护事件位点的投影运行器
     * @param views     当前运行时注册的投影视图集合
     * @return 负责实际投影启停的共享管理器
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
     * 声明 Spring 是否应随应用上下文自动启动视图管理器。
     *
     * @return 是否启用自动启动
     */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * 声明当前组件在 Spring 生命周期中的启停顺序。
     *
     * @return 当前组件的生命周期阶段
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
