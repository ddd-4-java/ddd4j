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
package io.ddd4j.data.projection.javalin;

import io.ddd4j.core.cqrs.readmodel.*;
import io.javalin.Javalin;

import java.util.Collection;
import java.util.Objects;

/**
 * 旧 Javalin 工厂入口，视图生命周期委托共享管理器，线程池由本适配器拥有。
 */
public class JavalinProjectionViewManager implements ViewManager, AutoCloseable {
    /** \u8d1f\u8d23\u5b89\u6392\u6295\u5f71\u89e6\u53d1\u548c\u53d6\u6d88\u7684\u8c03\u5ea6\u5668\u3002 */
    private final JavalinProjectionScheduler scheduler;
    /** \u5b9e\u73b0\u5b9e\u9645\u89c6\u56fe\u8c03\u5ea6\u548c\u751f\u547d\u5468\u671f\u7684\u5171\u4eab\u7ba1\u7406\u5668\u3002 */
    private final DefaultViewManager<Object> delegate;

    /**
     * \u88c5\u914d Javalin \u7684\u6295\u5f71\u8c03\u5ea6\u5668\u3001\u6267\u884c\u5668\u548c\u89c6\u56fe\u3002
     * @param scheduler \u8c03\u5ea6\u6295\u5f71\u4efb\u52a1\u7684\u8fd0\u884c\u65f6\u7ec4\u4ef6
     * @param runner \u8c03\u7528\u89c6\u56fe\u5e76\u7ef4\u62a4\u4e8b\u4ef6\u4f4d\u70b9\u7684\u6295\u5f71\u8fd0\u884c\u5668
     * @param views \u5f53\u524d\u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u6295\u5f71\u89c6\u56fe\u96c6\u5408
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
