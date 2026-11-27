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
package io.ddd4j.micronaut.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionCron;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Micronaut CDI 投影调度器（{@link ViewScheduler} SPI 的 Micronaut 实现）。
 *
 * <p>由 Micronaut 工厂或旧 GAV 的 Bean 管理所有权，使用 JDK 调度器和共享数值 CRON。
 * 保留完成后重调度与代际 Future 取消保护。
 *
 * <p><b>仅服务 Micronaut 运行时</b>；Spring 系运行时用
 * {@code SpringProjectionScheduler}，Quarkus 用 {@code QuarkusProjectionScheduler}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewScheduler
 * @since 2.0.x
 */
public class MicronautProjectionScheduler implements ViewScheduler {

    /** \u6309\u8ba1\u5212\u6267\u884c\u5f53\u524d\u7ec4\u4ef6\u540e\u53f0\u4efb\u52a1\u7684\u5b9a\u65f6\u7ebf\u7a0b\u6c60\u3002 */
    private final ScheduledExecutorService executor;

    /**
     * 创建由框架管理的调度器。
     */
    public MicronautProjectionScheduler() {
        this(Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "micronaut-projection-scheduler");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /**
     * 使用指定执行器，关闭时终止该执行器。
     *
     * @param executor 调度执行器
     */
    protected MicronautProjectionScheduler(ScheduledExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * 关闭本调度器拥有的执行器。
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    /** {@inheritDoc} */
    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        Objects.requireNonNull(viewName, "viewName must not be null");
        Objects.requireNonNull(cron, "cron must not be null");
        Objects.requireNonNull(task, "task must not be null");
        CronExpression cronExpr = CronExpression.parse(cron);
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
        private final CronExpression cron;
        /** \u7531\u5f53\u524d\u8c03\u5ea6\u53e5\u67c4\u6267\u884c\u7684\u6295\u5f71\u4efb\u52a1\u3002 */
        private final Runnable task;
        /** \u7528\u4e8e\u8ddf\u8e2a\u548c\u53d6\u6d88\u540e\u53f0\u8c03\u5ea6\u4efb\u52a1\u7684\u53e5\u67c4\u3002 */
        private volatile ScheduledFuture<?> future;
        /** \u4efb\u52a1\u662f\u5426\u5df2\u53d6\u6d88\u5e76\u505c\u6b62\u7ee7\u7eed\u5b89\u6392\u65b0\u7684\u89e6\u53d1\u3002 */
        private volatile boolean cancelled = false;
        /** \u6807\u8bc6\u5f53\u524d\u8ba1\u5212\u7248\u672c\uff0c\u4f9b\u505c\u7528\u5df2\u5931\u6548\u7684\u5f02\u6b65\u89e6\u53d1\u3002 */
        private long scheduleGeneration;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>ReschedulingHandle</code> \u5b9e\u4f8b\u3002
         * @param executor \u6309\u8ba1\u5212\u6267\u884c\u5f53\u524d\u7ec4\u4ef6\u540e\u53f0\u4efb\u52a1\u7684\u5b9a\u65f6\u7ebf\u7a0b\u6c60
         * @param cron <code>cron</code> \u5b57\u6bb5
         * @param task <code>task</code> \u5b57\u6bb5
         */
        ReschedulingHandle(ScheduledExecutorService executor, CronExpression cron, Runnable task) {
            this.executor = executor;
            this.cron = cron;
            this.task = task;
        }

        /** \u6839\u636e Cron \u8ba1\u7b97\u4e0b\u4e00\u6b21\u6267\u884c\u65f6\u95f4\uff0c\u5e76\u5728\u8ba1\u5212\u4ecd\u6709\u6548\u65f6\u5411\u8c03\u5ea6\u7ebf\u7a0b\u6c60\u6ce8\u518c\u89e6\u53d1\u3002 */
        synchronized void scheduleNext() {
            if (cancelled) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime next = cron.next(now);
            if (Objects.isNull(next)) {
                return;
            }
            long delayMillis = Duration.between(now, next).toMillis();
            if (delayMillis < 0) {
                delayMillis = 0;
            }
            // 立即执行的任务可能先完成重调度，旧 Future 不得覆盖新的句柄。
            long generation = ++scheduleGeneration;
            ScheduledFuture<?> scheduled = executor.schedule(this, delayMillis, TimeUnit.MILLISECONDS);
            if (generation == scheduleGeneration) {
                future = scheduled;
            }
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public synchronized void cancel() {
            cancelled = true;
            ScheduledFuture<?> f = future;
            if (Objects.nonNull(f)) {
                f.cancel(false);
            }
        }

        /** {@inheritDoc} */
        @Override
        public synchronized boolean isActive() {
            if (cancelled) {
                return false;
            }
            ScheduledFuture<?> f = future;
            return Objects.nonNull(f) && !f.isCancelled() && !f.isDone();
        }
    }

    /**
     * 保留六段数值语法，时间搜索复用 core；保留本地时区语义。
     */
    public static class CronExpression {
        /** \u8d1f\u8d23\u89e3\u6790 Cron \u5b57\u6bb5\u5e76\u8ba1\u7b97\u4e0b\u6b21\u89e6\u53d1\u65f6\u95f4\u7684\u5171\u7528\u5b9e\u73b0\u3002 */
        private final ProjectionCron delegate;

        /**
         * \u628a\u5df2\u89e3\u6790\u7684 Cron \u8ba1\u5212\u5305\u88c5\u6210\u5f53\u524d\u6846\u67b6\u7684\u8c03\u5ea6\u8868\u8fbe\u5f0f\u3002
         * @param delegate \u6267\u884c\u5171\u4eab\u884c\u4e3a\u7684\u5e95\u5c42\u5b9e\u73b0
         */
        private CronExpression(ProjectionCron delegate) {
            this.delegate = delegate;
        }

        /**
         * 解析旧入口的表达式。
         *
         * @param expression 六段数值表达式
         * @return 已解析对象
         */
        public static CronExpression parse(String expression) {
            if (expression.contains("?")) {
                throw new IllegalArgumentException("Micronaut legacy cron does not support ?: " + expression);
            }
            return new CronExpression(ProjectionCron.parse(expression));
        }

        /**
         * 从本地时间查找下一时间。
         *
         * @param from 起始本地时间
         * @return 下一时间，未来八年无匹配返回 null
         */
        public LocalDateTime next(LocalDateTime from) {
            // 旧实现按 LocalDateTime 墙上时间递增；固定偏移仅用于复用解析器，避免引入 DST 转换。
            java.time.ZonedDateTime next = delegate.next(from.atZone(java.time.ZoneOffset.UTC));
            return Objects.isNull(next) ? null : next.toLocalDateTime();
        }
    }
}
