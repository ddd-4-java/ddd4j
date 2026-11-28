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
package io.ddd4j.guice.projection.javalin;

import io.ddd4j.core.cqrs.readmodel.*;
import io.javalin.Javalin;

import java.util.Collection;
import java.util.Objects;

/**
 * 旧 Javalin 工厂入口，视图生命周期委托共享管理器，线程池由本适配器拥有。
 */
public class JavalinProjectionViewManager implements ViewManager, AutoCloseable {
    /**
     * 负责安排投影触发和取消的调度器。
     */
    private final JavalinProjectionScheduler scheduler;
    /**
     * 实现实际视图调度和生命周期的共享管理器。
     */
    private final DefaultViewManager<Object> delegate;

    /**
     * 装配 Javalin 的投影调度器、执行器和视图。
     *
     * @param scheduler 调度投影任务的运行时组件
     * @param runner    调用视图并维护事件位点的投影运行器
     * @param views     当前运行时注册的投影视图集合
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private JavalinProjectionViewManager(JavalinProjectionScheduler scheduler,
                                         ProjectionRunner<?> runner,
                                         Collection<ProjectionView<?>> views) {
        this.scheduler = scheduler;
        delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner, (Collection) views);
    }

    /**
     * 创建兼容管理器，不自动启动。应用关闭时应调用 close，stop 仍允许重新启动。
     *
     * @param app    Javalin 装配锚点
     * @param views  投影视图
     * @param runner 共享投影运行器
     * @return 管理器
     */
    public static JavalinProjectionViewManager create(Javalin app,
                                                      Collection<ProjectionView<?>> views,
                                                      ProjectionRunner<?> runner) {
        Objects.requireNonNull(app, "app must not be null");
        Objects.requireNonNull(views, "views must not be null");
        Objects.requireNonNull(runner, "runner must not be null");
        JavalinProjectionScheduler scheduler = JavalinProjectionScheduler.create(app, views, runner);
        return new JavalinProjectionViewManager(scheduler, runner, views);
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>Javalin 实现调用 <code>delegate.start()</code>。</p>
     */
    @Override
    public void start() {
        delegate.start();
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>Javalin 实现调用 <code>delegate.stop()</code>。</p>
     */
    @Override
    public void stop() {
        delegate.stop();
    }

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * <p>Javalin 实现调用 <code>delegate.isRunning()</code>。</p>
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
     * <p>Javalin 实现调用 <code>delegate.triggerOnce()</code>。</p>
     */
    @Override
    public void triggerOnce() {
        delegate.triggerOnce();
    }

    /**
     * 汇总当前登记的投影视图及其运行状态。
     *
     * <p>Javalin 实现调用 <code>delegate.getProjectionStatus(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 实际返回<code>delegate.getProjectionStatus(streamId)</code>
     */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return delegate.getProjectionStatus(streamId);
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>Javalin 实现调用 <code>stop()</code>。</p>
     */
    @Override
    public void close() {
        try {
            stop();
        } finally {
            scheduler.shutdown();
        }
    }
}
