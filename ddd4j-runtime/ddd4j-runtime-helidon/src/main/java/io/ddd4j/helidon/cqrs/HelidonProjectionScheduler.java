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
package io.ddd4j.helidon.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.core.cqrs.readmodel.ProjectionCron;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Helidon 的 JDK 调度适配器：CRON 解析复用 core，调度线程由本实例拥有。
 * SE 调用方显式 shutdown；MP Extension 仅关闭自行创建的实例。
 * 保留旧六段数字方言、本地墙上时间和一百万秒搜索窗口。
 */
public class HelidonProjectionScheduler implements ViewScheduler {

    private final Set<ReschedulingHandle> handles = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "helidon-projection-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * 关闭调度器（释放线程池资源）。
     *
     * <p><b>Helidon SE 模式</b>：无 {@code @PreDestroy} 自动触发——集成方需在
     * 应用关闭时手动调用，或注册 Helidon {@code io.helidon.common.context.Context}
     * shutdown hook 触发。
     */
    public synchronized void shutdown() {
        handles.forEach(ReschedulingHandle::cancel);
        handles.clear();
        executor.shutdownNow();
    }

    @Override
    public synchronized ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        Objects.requireNonNull(viewName, "viewName must not be null");
        Objects.requireNonNull(cron, "cron must not be null");
        Objects.requireNonNull(task, "task must not be null");
        CronExpression cronExpr = CronExpression.parse(cron);
        ReschedulingHandle handle = new ReschedulingHandle(executor, cronExpr, task);
        handle.scheduleNext();
        handles.add(handle);
        return handle;
    }

    /**
     * 自动重调度的 ViewScheduleHandle：每次任务执行完毕后计算下次延迟并重新提交。
     */
    private class ReschedulingHandle implements ViewScheduleHandle, Runnable {

        private final ScheduledExecutorService executor;
        private final CronExpression cron;
        private final Runnable task;
        private volatile ScheduledFuture<?> future;
        private volatile boolean cancelled = false;

        ReschedulingHandle(ScheduledExecutorService executor, CronExpression cron, Runnable task) {
            this.executor = executor;
            this.cron = cron;
            this.task = task;
        }

        synchronized void scheduleNext() {
            if (cancelled || executor.isShutdown()) {
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
            future = executor.schedule(this, delayMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public void run() {
            if (cancelled || executor.isShutdown()) {
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
            handles.remove(this);
            ScheduledFuture<?> f = future;
            if (Objects.nonNull(f)) {
                f.cancel(false);
            }
        }

        @Override
        public boolean isActive() {
            if (cancelled || executor.isShutdown()) {
                return false;
            }
            ScheduledFuture<?> f = future;
            return Objects.nonNull(f) && !f.isCancelled() && !f.isDone();
        }
    }

    /** 旧 SE 六段数字语法的薄桥；墙上时间算法交给共享解析器。 */
    public static class CronExpression {
        private final ProjectionCron delegate;
        private CronExpression(ProjectionCron delegate) { this.delegate = delegate; }
        /** 解析数字表达式，不接受旧接口未支持的问号和范围步长。
         * @param expression 六段表达式
         * @return 解析结果
         */
        public static CronExpression parse(String expression) {
            Objects.requireNonNull(expression, "expression must not be null");
            if (expression.contains("?") || expression.matches(".*[0-9]-[0-9]+/.*")) {
                throw new IllegalArgumentException("Helidon legacy cron does not support this syntax: " + expression);
            }
            return new CronExpression(ProjectionCron.parse(expression));
        }
        /** 保持原 SE 的本地墙上时间及一百万秒搜索窗口。
         * @param from 本地时间
         * @return 下一执行时间，窗口内没有匹配返回 null
         */
        public LocalDateTime next(LocalDateTime from) {
            ZonedDateTime next = delegate.next(from.atZone(ZoneOffset.UTC));
            if (Objects.isNull(next)) { return null; }
            LocalDateTime local = next.toLocalDateTime();
            return local.isAfter(from.plusSeconds(1_000_000).withNano(0)) ? null : local;
        }
    }
}
