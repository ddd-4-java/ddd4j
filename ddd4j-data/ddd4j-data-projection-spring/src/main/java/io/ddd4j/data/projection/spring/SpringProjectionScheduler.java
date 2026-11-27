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

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Spring CRON 投影调度器（{@link ViewScheduler} SPI 的 Spring 实现）。
 *
 * <p>以 {@code @Component} + {@link SmartLifecycle} 实现 Spring 生命周期适配：
 * 构造器注入 {@link TaskScheduler}，{@link #schedule} 用 {@link CronTrigger}
 * 注册定时任务，{@link SmartLifecycle#start()} 遍历已注册 views 启动所有调度，
 * {@link SmartLifecycle#stop()} 取消全部。{@link ViewScheduleHandle} 包装
 * {@link ScheduledFuture}（cancel 调 future.cancel）。
 *
 * <p><b>仅服务 Spring 系运行时</b>（WebMVC/WebFlux/Helidon-Spring）；
 * Quarkus 运行时用 {@code QuarkusProjectionScheduler}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewScheduler
 * @see SmartLifecycle
 * @since 2.0.x
 */
@Component
public class SpringProjectionScheduler implements ViewScheduler, SmartLifecycle {

    /** \u8d1f\u8d23\u5b89\u6392 Spring \u6295\u5f71\u89e6\u53d1\u7684\u7ebf\u7a0b\u8c03\u5ea6\u5668\u3002 */
    private final TaskScheduler taskScheduler;

    /** \u5f53\u524d\u5df2\u7ecf\u6ce8\u518c\u7684\u6295\u5f71\u4efb\u52a1\u53ca\u5176\u53d6\u6d88\u53e5\u67c4\u3002 */
    private final Map<String, ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    /** \u5f53\u524d Spring \u8c03\u5ea6\u5668\u662f\u5426\u8fd0\u884c\u3002 */
    private volatile boolean running = false;

    /**
     * \u521b\u5efa Spring \u6295\u5f71\u8c03\u5ea6\u9002\u914d\u5668\u3002
     * @param taskScheduler Spring \u4efb\u52a1\u8c03\u5ea6\u5668
     */
    @Autowired
    public SpringProjectionScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler must not be null");
    }

    /**
     * 按给定时间规则安排任务。
     *
     * <p>Spring 实现调用 <code>taskScheduler.schedule(task, new CronTrigger(cron))</code>。</p>
     *
     * @param viewName 视图名称（用于日志 / 调试）
     * @param cron CRON 表达式
     * @param task 要执行的任务
     * @return 实际返回<code>handle</code>
     */
    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        Objects.requireNonNull(viewName, "viewName must not be null");
        Objects.requireNonNull(cron, "cron must not be null");
        Objects.requireNonNull(task, "task must not be null");
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cron));
        SpringViewScheduleHandle handle = new SpringViewScheduleHandle(future);
        handles.put(viewName, handle);
        return handle;
    }

    /** \u5141\u8bb8\u5f53\u524d Spring \u6295\u5f71\u8c03\u5ea6\u5668\u63a5\u53d7\u548c\u6267\u884c\u4efb\u52a1\u3002 */
    @Override
    public void start() {
        if (!running) {
            running = true;
        }
    }

    /** \u505c\u6b62\u5e76\u91ca\u653e\u5f53\u524d Spring \u6295\u5f71\u8c03\u5ea6\u5668\u5b89\u6392\u7684\u4efb\u52a1\u3002 */
    @Override
    public void stop() {
        if (running) {
            running = false;
            handles.values().forEach(ViewScheduleHandle::cancel);
            handles.clear();
        }
    }

    /**
     * \u8bfb\u53d6<code>running</code> \u5b57\u6bb5\u3002
     * @return <code>running</code> \u5b57\u6bb5
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * \u58f0\u660e\u5f53\u524d\u7ec4\u4ef6\u5728 Spring \u751f\u547d\u5468\u671f\u4e2d\u7684\u542f\u505c\u987a\u5e8f\u3002
     * @return \u5f53\u524d\u7ec4\u4ef6\u7684\u751f\u547d\u5468\u671f\u9636\u6bb5
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * Spring {@link ScheduledFuture} 适配为 ddd4j {@link ViewScheduleHandle}。
     */
    private static class SpringViewScheduleHandle implements ViewScheduleHandle {

        /** \u7528\u4e8e\u8ddf\u8e2a\u548c\u53d6\u6d88\u540e\u53f0\u8c03\u5ea6\u4efb\u52a1\u7684\u53e5\u67c4\u3002 */
        private final ScheduledFuture<?> future;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>SpringViewScheduleHandle</code> \u5b9e\u4f8b\u3002
         * @param future \u7528\u4e8e\u8ddf\u8e2a\u548c\u53d6\u6d88\u540e\u53f0\u8c03\u5ea6\u4efb\u52a1\u7684\u53e5\u67c4
         */
        SpringViewScheduleHandle(ScheduledFuture<?> future) {
            this.future = future;
        }

        /**
         * 撤销当前任务句柄允许撤销的操作。
         *
         * <p>Spring 实现调用 <code>future.cancel(false)</code>。</p>
         */
        @Override
        public void cancel() {
            future.cancel(false);
        }

        /**
         * 检查当前句柄是否仍处于有效状态。
         *
         * <p>Spring 实现调用 <code>future.isCancelled()</code>。</p>
         *
         * @return 实际返回<code>!future.isCancelled() &amp;&amp; !future.isDone()</code>
         */
        @Override
        public boolean isActive() {
            return !future.isCancelled() && !future.isDone();
        }
    }
}
