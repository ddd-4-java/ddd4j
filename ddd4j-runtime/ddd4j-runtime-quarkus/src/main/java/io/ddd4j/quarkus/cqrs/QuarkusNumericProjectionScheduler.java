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

    /** \u6309\u8ba1\u5212\u6267\u884c\u5f53\u524d\u7ec4\u4ef6\u540e\u53f0\u4efb\u52a1\u7684\u5b9a\u65f6\u7ebf\u7a0b\u6c60\u3002 */
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
     * @param cron CRON 表达式
     * @param task 要执行的任务
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

        /** \u6309\u8ba1\u5212\u6267\u884c\u5f53\u524d\u7ec4\u4ef6\u540e\u53f0\u4efb\u52a1\u7684\u5b9a\u65f6\u7ebf\u7a0b\u6c60\u3002 */
        private final ScheduledExecutorService executor;
        /** \u7528\u6765\u786e\u5b9a\u4efb\u52a1\u4e0b\u6b21\u89e6\u53d1\u65f6\u95f4\u7684 Cron \u8868\u8fbe\u5f0f\u3002 */
        private final ProjectionCron cron;
        /** \u7531\u5f53\u524d\u8c03\u5ea6\u53e5\u67c4\u6267\u884c\u7684\u6295\u5f71\u4efb\u52a1\u3002 */
        private final Runnable task;
        /** \u7528\u4e8e\u8ddf\u8e2a\u548c\u53d6\u6d88\u540e\u53f0\u8c03\u5ea6\u4efb\u52a1\u7684\u53e5\u67c4\u3002 */
        private volatile ScheduledFuture<?> future;
        /** \u4efb\u52a1\u662f\u5426\u5df2\u53d6\u6d88\u5e76\u505c\u6b62\u7ee7\u7eed\u5b89\u6392\u65b0\u7684\u89e6\u53d1\u3002 */
        private volatile boolean cancelled = false;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>ReschedulingHandle</code> \u5b9e\u4f8b\u3002
         * @param executor \u6309\u8ba1\u5212\u6267\u884c\u5f53\u524d\u7ec4\u4ef6\u540e\u53f0\u4efb\u52a1\u7684\u5b9a\u65f6\u7ebf\u7a0b\u6c60
         * @param cron <code>cron</code> \u5b57\u6bb5
         * @param task <code>task</code> \u5b57\u6bb5
         */
        ReschedulingHandle(ScheduledExecutorService executor, ProjectionCron cron, Runnable task) {
            this.executor = executor;
            this.cron = cron;
            this.task = task;
        }

        /** \u6839\u636e Cron \u8ba1\u7b97\u4e0b\u4e00\u6b21\u6267\u884c\u65f6\u95f4\uff0c\u5e76\u5728\u8ba1\u5212\u4ecd\u6709\u6548\u65f6\u5411\u8c03\u5ea6\u7ebf\u7a0b\u6c60\u6ce8\u518c\u89e6\u53d1\u3002 */
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
