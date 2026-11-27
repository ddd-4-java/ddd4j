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
package io.ddd4j.data.projection.micronaut;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.micronaut.context.BeanContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Micronaut CDI 投影视图管理器（{@link ViewManager} SPI 的 Micronaut 实现）。
 *
 * <p>以 {@code @Singleton} 实现：{@link BeanContext#getBeansOfType(Class)} 收集所有
 * {@link ProjectionView} Beans + {@link ProjectionRunner}；
 * {@link PostConstruct} 触发 {@link #start()}（遍历 views 按 cron 调
 * {@link ViewScheduler#schedule}），{@link PreDestroy} 触发 {@link #stop()}
 * （取消全部 handle），{@link #triggerOnce()} 立即执行一次所有 view 的 runOnce。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see ProjectionRunner
 * @since 2.0.x
 */
@Singleton
public class MicronautProjectionViewManager implements ViewManager {

    /** 注册任务和取消任务所用的视图调度器。 */
    private final ViewScheduler scheduler;

    /** 实际执行投影更新的运行器。 */
    private final ProjectionRunner<?> runner;

    /** 已发现并参加调度的投影视图。 */
    private final Collection<ProjectionView<?>> views;

    /** 以视图名称索引的当前调度任务句柄。 */
    private final Map<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    /** 视图管理器是否已启动。 */
    private volatile boolean running = false;

    /**
     * \u6536\u96c6\u539f\u751f\u89c6\u56fe\uff0c\u4fdd\u7559\u6784\u9020\u65f6\u5feb\u7167\u3002
     * @param scheduler \u8c03\u5ea6\u5668
     * @param runner \u8fd0\u884c\u5668
     * @param context \u5bb9\u5668
     */
    @Inject
    @SuppressWarnings("unchecked")
    public MicronautProjectionViewManager(ViewScheduler scheduler,
                                          ProjectionRunner<?> runner,
                                          BeanContext context) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = (Collection<ProjectionView<?>>) (Collection<?>) context.getBeansOfType(ProjectionView.class);
    }

    /**
     * 无参构造（<b>仅供 Micronaut AOP 代理机制使用，业务代码不得调用</b>）：
     * {@code @Singleton} 客户端代理在构建期生成的子类需要一个可调用的
     * 非私有无参构造。
     */
    protected MicronautProjectionViewManager() {
        this.scheduler = null;
        this.runner = null;
        this.views = null;
    }

    /** 容器初始化组件后启动投影视图管理器。 */
    @PostConstruct
    void onPostConstruct() {
        start();
    }

    /** 容器销毁组件前停止管理器并释放调度任务。 */
    @PreDestroy
    void onPreDestroy() {
        stop();
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>Micronaut 实现调用 <code>view.getName()</code>。</p>
     */
    @Override
    public void start() {
        if (!running && scheduler != null && views != null) {
            running = true;
            for (ProjectionView<?> view : views) {
                ViewScheduler.ViewScheduleHandle handle = scheduler.schedule(
                        view.getName(), view.getCron(), () -> runView(view));
                handles.put(view.getName(), handle);
            }
        }
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>Micronaut 实现调用 <code>handles.values()</code>。</p>
     */
    @Override
    public void stop() {
        if (running) {
            running = false;
            handles.values().forEach(ViewScheduler.ViewScheduleHandle::cancel);
            handles.clear();
        }
    }

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * @return 实际返回<code>running</code>，作为本次判断结果
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 请求立即执行指定投影视图的一轮更新。
     *
     * <p>Micronaut 实现调用 <code>((ProjectionRunner&lt;Object&gt;)runner).runOnce((ProjectionView&lt;Object&gt;)view)</code>。</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public void triggerOnce() {
        if (views != null) {
            for (ProjectionView<?> view : views) {
                ((ProjectionRunner<Object>) runner).runOnce((ProjectionView<Object>) view);
            }
        }
    }

    /**
     * 将本次调度触发的视图交给投影运行器执行。
     *
     * @param view 本次要执行投影更新的视图
     */
    @SuppressWarnings("unchecked")
    private void runView(ProjectionView<?> view) {
        ((ProjectionRunner<Object>) runner).runOnce((ProjectionView<Object>) view);
    }
}
