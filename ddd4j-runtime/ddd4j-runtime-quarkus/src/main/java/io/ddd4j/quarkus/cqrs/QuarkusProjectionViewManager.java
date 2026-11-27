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
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quarkus CDI 投影视图管理器（{@link ViewManager} SPI 的 Quarkus 实现）。
 *
 * <p>以 {@code @ApplicationScoped} 实现：ArC {@link Instance} 收集所有
 * {@link ProjectionView} Beans + {@link ProjectionRunner}；
 * {@link StartupEvent} 事件触发 {@link #start()}（遍历 views 按 cron 调
 * {@link ViewScheduler#schedule}），{@link Shutdown} 事件触发 {@link #stop()}
 * （取消全部 handle），{@link #triggerOnce()} 立即执行一次所有 view 的 runOnce。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see ProjectionRunner
 * @since 2.0.x
 */
@ApplicationScoped
public class QuarkusProjectionViewManager implements ViewManager {

    /** \u8d1f\u8d23\u5b89\u6392\u6295\u5f71\u89e6\u53d1\u548c\u53d6\u6d88\u7684\u8c03\u5ea6\u5668\u3002 */
    private final ViewScheduler scheduler;

    /** \u5b9e\u9645\u8bfb\u53d6\u5e76\u5e94\u7528\u6295\u5f71\u4e8b\u4ef6\u7684\u6267\u884c\u5668\u3002 */
    private final ProjectionRunner<?> runner;

    /** \u7531\u5f53\u524d\u8fd0\u884c\u73af\u5883\u7ba1\u7406\u548c\u8c03\u5ea6\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    private final Instance<ProjectionView<?>> views;

    /** 以视图名称索引的当前调度任务句柄。 */
    private final Map<String, ViewScheduler.ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    /** 视图管理器是否已启动。 */
    private volatile boolean running = false;

    /**
     * \u4ece\u65e7 CDI \u6ce8\u5165\u70b9\u88c5\u914d\uff1b\u89c6\u56fe\u4ecd\u4e8e\u9996\u6b21\u4f7f\u7528\u65f6\u53d1\u73b0\u3002
     * @param scheduler \u539f\u751f\u89c6\u56fe\u8c03\u5ea6\u5668
     * @param runner \u5171\u4eab\u8fd0\u884c\u5668
     * @param views \u89c6\u56fe\u53d1\u73b0\u5165\u53e3
     */
    @Inject
    public QuarkusProjectionViewManager(ViewScheduler scheduler,
                                        ProjectionRunner<?> runner,
                                        Instance<ProjectionView<?>> views) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
        this.views = Objects.requireNonNull(views, "views must not be null");
    }

    /**
     * 无参构造（<b>仅供 ArC 代理机制使用，业务代码不得调用</b>）：
     * {@code @ApplicationScoped} 客户端代理在构建期生成的子类需要一个可调用的
     * 非私有无参构造。
     */
    protected QuarkusProjectionViewManager() {
        this.scheduler = null;
        this.runner = null;
        this.views = null;
    }

    /**
     * 收到 Quarkus 启动回调后登记投影视图调度。
     *
     * @param event 传入此操作的 <code>StartupEvent</code> 类型 <code>event</code>
     */
    void onStart(@Observes StartupEvent event) {
        start();
    }

    /**
     * 收到 Quarkus 关闭回调后取消投影视图调度。
     *
     * @param event 传入此操作的 <code>Shutdown</code> 类型 <code>event</code>
     */
    void onStop(@Observes Shutdown event) {
        stop();
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>Quarkus 实现调用 <code>view.getName()</code>。</p>
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
     * <p>Quarkus 实现调用 <code>handles.values()</code>。</p>
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
     * <p>Quarkus 实现调用 <code>((ProjectionRunner&lt;Object&gt;)runner).runOnce((ProjectionView&lt;Object&gt;)view)</code>。</p>
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
