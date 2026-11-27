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

import io.ddd4j.core.cqrs.readmodel.ProjectionCron;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Quarkus CDI 投影调度器（{@link ViewScheduler} SPI 的 Quarkus 实现）。
 *
 * <p>以 {@code @ApplicationScoped} 实现。Quarkus 3.x 编程式调度 API 不可用时
 * 退化为 JDK {@link ScheduledExecutorService} 兜底（与 javalin 模式同款）：
 * {@link #schedule} 用 {@link ProjectionCron} 解析 cron 得到下次执行延迟，
 * 以 {@code scheduleWithFixedDelay} 注册并自动重调度。
 *
 * <p><b>仅服务 Quarkus 运行时</b>；Spring 系运行时用
 * {@code SpringProjectionScheduler}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewScheduler
 * @since 2.0.x
 */
public class QuarkusNumericProjectionScheduler implements ViewScheduler {

    /**
     * 按计划执行当前组件后台任务的定时线程池。
     */
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "quarkus-projection-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * 关闭本适配器拥有的数值 cron 线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 按给定时间规则安排任务。
     *
     * <p>Quarkus 实现调用 <code>ProjectionCron.parse(cron)</code>。</p>
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
        ProjectionCron cronExpr = ProjectionCron.parse(cron);
        ReschedulingHandle handle = new ReschedulingHandle(executor, cronExpr, task);
        handle.scheduleNext();
        return handle;
    }

    /**
     * 自动重调度的 ViewScheduleHandle：每次任务执行完毕后计算下次延迟并重新提交。
     */
    private static class ReschedulingHandle implements ViewScheduleHandle, Runnable {

        /**
         * 按计划执行当前组件后台任务的定时线程池。
         */
        private final ScheduledExecutorService executor;
        /**
         * 用来确定任务下次触发时间的 Cron 表达式。
         */
        private final ProjectionCron cron;
        /**
         * 由当前调度句柄执行的投影任务。
         */
        private final Runnable task;
        /**
         * 用于跟踪和取消后台调度任务的句柄。
         */
        private volatile ScheduledFuture<?> future;
        /**
         * 任务是否已取消并停止继续安排新的触发。
         */
        private volatile boolean cancelled = false;

        /**
         * 用给定值初始化 <code>ReschedulingHandle</code> 实例。
         *
         * @param executor 按计划执行当前组件后台任务的定时线程池
         * @param cron     <code>cron</code> 字段
         * @param task     <code>task</code> 字段
         */
        ReschedulingHandle(ScheduledExecutorService executor, ProjectionCron cron, Runnable task) {
            this.executor = executor;
            this.cron = cron;
            this.task = task;
        }

        /**
         * 根据 Cron 计算下一次执行时间，并在计划仍有效时向调度线程池注册触发。
         */
        void scheduleNext() {
            if (cancelled) {
                return;
            }
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime next = cron.next(now);
            if (Objects.isNull(next)) {
                return;
            }
            long delayMillis = Duration.between(now, next).toMillis();
            if (delayMillis < 0) {
                delayMillis = 0;
            }
            future = executor.schedule(this, delayMillis, TimeUnit.MILLISECONDS);
        }

        /**
         * 执行当前组件的任务体。
         *
         * <p>此实现调用 <code>task.run()</code>。</p>
         */
        @Override
        public void run() {
            if (cancelled) {
                return;
            }
            try {
                task.run();
            } finally {
                scheduleNext();
            }
        }

        /**
         * 撤销当前任务句柄允许撤销的操作。
         *
         * <p>此实现调用 <code>f.cancel(false)</code>。</p>
         */
        @Override
        public void cancel() {
            cancelled = true;
            ScheduledFuture<?> f = future;
            if (Objects.nonNull(f)) {
                f.cancel(false);
            }
        }

        /**
         * 检查当前句柄是否仍处于有效状态。
         *
         * <p>此实现调用 <code>f.isCancelled()</code>。</p>
         *
         * @return 根据执行路径返回<code>false</code>、<code>Objects.nonNull(f) &amp;&amp; !f.isCancelled() &amp;&amp; !f.isDone()</code>
         */
        @Override
        public boolean isActive() {
            if (cancelled) {
                return false;
            }
            ScheduledFuture<?> f = future;
            return Objects.nonNull(f) && !f.isCancelled() && !f.isDone();
        }
    }

}
