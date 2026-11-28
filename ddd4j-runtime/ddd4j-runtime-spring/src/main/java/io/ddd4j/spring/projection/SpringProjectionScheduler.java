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

    /**
     * 负责安排 Spring 投影触发的线程调度器。
     */
    private final TaskScheduler taskScheduler;

    /**
     * 当前已经注册的投影任务及其取消句柄。
     */
    private final Map<String, ViewScheduleHandle> handles = new ConcurrentHashMap<>();

    /**
     * 当前 Spring 调度器是否运行。
     */
    private volatile boolean running = false;

    /**
     * 创建 Spring 投影调度适配器。
     *
     * @param taskScheduler Spring 任务调度器
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
     * @param cron     CRON 表达式
     * @param task     要执行的任务
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

    /**
     * 允许当前 Spring 投影调度器接受和执行任务。
     */
    @Override
    public void start() {
        if (!running) {
            running = true;
        }
    }

    /**
     * 停止并释放当前 Spring 投影调度器安排的任务。
     */
    @Override
    public void stop() {
        if (running) {
            running = false;
            handles.values().forEach(ViewScheduleHandle::cancel);
            handles.clear();
        }
    }

    /**
     * 读取<code>running</code> 字段。
     *
     * @return <code>running</code> 字段
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 声明当前组件在 Spring 生命周期中的启停顺序。
     *
     * @return 当前组件的生命周期阶段
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * Spring {@link ScheduledFuture} 适配为 ddd4j {@link ViewScheduleHandle}。
     */
    private static class SpringViewScheduleHandle implements ViewScheduleHandle {

        /**
         * 用于跟踪和取消后台调度任务的句柄。
         */
        private final ScheduledFuture<?> future;

        /**
         * 用给定值初始化 <code>SpringViewScheduleHandle</code> 实例。
         *
         * @param future 用于跟踪和取消后台调度任务的句柄
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
