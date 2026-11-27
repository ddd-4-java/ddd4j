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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
     * \u4ece Spring \u5bb9\u5668\u88c5\u914d\u6295\u5f71\u89c6\u56fe\u7ba1\u7406\u5668\u3002
     * @param scheduler \u89c6\u56fe\u8c03\u5ea6\u5668
     * @param runner \u6295\u5f71\u6267\u884c\u5668
     * @param views \u53ef\u7528\u7684\u6295\u5f71\u89c6\u56fe
     */
    @Autowired
    public SpringProjectionViewManager(ViewScheduler scheduler,
                                       ProjectionRunner<?> runner,
                                       Collection<ProjectionView<?>> views) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = Objects.requireNonNull(views, "views must not be null");
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>Spring 实现调用 <code>view.getName()</code>。</p>
     */
    @Override
    public void start() {
        if (!running) {
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
     * <p>Spring 实现调用 <code>handles.values()</code>。</p>
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
     * <p>Spring 实现调用 <code>((ProjectionRunner&lt;Object&gt;)runner).runOnce((ProjectionView&lt;Object&gt;)view)</code>。</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public void triggerOnce() {
        for (ProjectionView<?> view : views) {
            ((ProjectionRunner<Object>) runner).runOnce((ProjectionView<Object>) view);
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
