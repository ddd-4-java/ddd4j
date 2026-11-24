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

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.core.cqrs.readmodel.ProjectionCron;

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

    private final ScheduledExecutorService executor;

    /** 创建由框架管理的调度器。 */
    public MicronautProjectionScheduler() {
        this(Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "micronaut-projection-scheduler");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /** 使用指定执行器，关闭时终止该执行器。
     * @param executor 调度执行器
     */
    protected MicronautProjectionScheduler(ScheduledExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** 关闭本调度器拥有的执行器。 */
    public void shutdown() {
        executor.shutdownNow();
    }

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

        private final ScheduledExecutorService executor;
        private final CronExpression cron;
        private final Runnable task;
        private volatile ScheduledFuture<?> future;
        private volatile boolean cancelled = false;
        private long scheduleGeneration;

        ReschedulingHandle(ScheduledExecutorService executor, CronExpression cron, Runnable task) {
            this.executor = executor;
            this.cron = cron;
            this.task = task;
        }

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

        @Override
        public synchronized void cancel() {
            cancelled = true;
            ScheduledFuture<?> f = future;
            if (Objects.nonNull(f)) {
                f.cancel(false);
            }
        }

        @Override
        public synchronized boolean isActive() {
            if (cancelled) {
                return false;
            }
            ScheduledFuture<?> f = future;
            return Objects.nonNull(f) && !f.isCancelled() && !f.isDone();
        }
    }

    /** 保留六段数值语法，时间搜索复用 core；保留本地时区语义。 */
    public static class CronExpression {
        private final ProjectionCron delegate;
        private CronExpression(ProjectionCron delegate) {
            this.delegate = delegate;
        }
        /** 解析旧入口的表达式。
         * @param expression 六段数值表达式
         * @return 已解析对象
         */
        public static CronExpression parse(String expression) {
            if (expression.contains("?")) {
                throw new IllegalArgumentException("Micronaut legacy cron does not support ?: " + expression);
            }
            return new CronExpression(ProjectionCron.parse(expression));
        }
        /** 从本地时间查找下一时间。
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
