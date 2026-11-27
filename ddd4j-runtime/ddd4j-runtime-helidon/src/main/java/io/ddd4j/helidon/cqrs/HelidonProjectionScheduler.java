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

import javax.inject.Singleton;
import io.ddd4j.core.cqrs.readmodel.ProjectionCron;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Helidon 的 JDK 调度适配器：CRON 解析复用 core，调度线程由本实例拥有。
 * SE 调用方显式 shutdown；MP Extension 仅关闭自行创建的实例。
 * 保留旧六段数字方言、本地墙上时间和一百万秒搜索窗口。
 */
@Singleton
public class HelidonProjectionScheduler implements ViewScheduler {

    /**
     * 当前已经注册的投影任务及其取消句柄。
     */
    private final Set<ReschedulingHandle> handles = ConcurrentHashMap.newKeySet();

    /**
     * 按计划执行当前组件后台任务的定时线程池。
     */
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

    /**
     * 按给定时间规则安排任务。
     *
     * <p>Helidon 实现调用 <code>CronExpression.parse(cron)</code>。</p>
     *
     * @param viewName 视图名称（用于日志 / 调试）
     * @param cron     CRON 表达式
     * @param task     要执行的任务
     * @return 实际返回<code>handle</code>
     */
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
     * 旧 SE 六段数字语法的薄桥；墙上时间算法交给共享解析器。
     */
    public static class CronExpression {
        /**
         * 负责解析 Cron 字段并计算下次触发时间的共用实现。
         */
        private final ProjectionCron delegate;

        /**
         * 把已解析的 Cron 计划包装成当前框架的调度表达式。
         *
         * @param delegate 执行共享行为的底层实现
         */
        private CronExpression(ProjectionCron delegate) {
            this.delegate = delegate;
        }

        /**
         * 解析数字表达式，不接受旧接口未支持的问号和范围步长。
         *
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

        /**
         * 保持原 SE 的本地墙上时间及一百万秒搜索窗口。
         *
         * @param from 本地时间
         * @return 下一执行时间，窗口内没有匹配返回 null
         */
        public LocalDateTime next(LocalDateTime from) {
            ZonedDateTime next = delegate.next(from.atZone(ZoneOffset.UTC));
            if (Objects.isNull(next)) {
                return null;
            }
            LocalDateTime local = next.toLocalDateTime();
            return local.isAfter(from.plusSeconds(1_000_000).withNano(0)) ? null : local;
        }
    }

    /**
     * 自动重调度的 ViewScheduleHandle：每次任务执行完毕后计算下次延迟并重新提交。
     */
    private class ReschedulingHandle implements ViewScheduleHandle, Runnable {

        /**
         * 按计划执行当前组件后台任务的定时线程池。
         */
        private final ScheduledExecutorService executor;
        /**
         * 用来确定任务下次触发时间的 Cron 表达式。
         */
        private final CronExpression cron;
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
        ReschedulingHandle(ScheduledExecutorService executor, CronExpression cron, Runnable task) {
            this.executor = executor;
            this.cron = cron;
            this.task = task;
        }

        /**
         * 根据 Cron 计算下一次执行时间，并在计划仍有效时向调度线程池注册触发。
         */
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

        /**
         * 执行当前组件的任务体。
         *
         * <p>此实现调用 <code>executor.isShutdown()</code>。</p>
         */
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

        /**
         * 撤销当前任务句柄允许撤销的操作。
         *
         * <p>此实现调用 <code>handles.remove(this)</code>。</p>
         */
        @Override
        public synchronized void cancel() {
            cancelled = true;
            handles.remove(this);
            ScheduledFuture<?> f = future;
            if (Objects.nonNull(f)) {
                f.cancel(false);
            }
        }

        /**
         * 检查当前句柄是否仍处于有效状态。
         *
         * <p>此实现调用 <code>executor.isShutdown()</code>。</p>
         *
         * @return 根据执行路径返回<code>false</code>、<code>Objects.nonNull(f) &amp;&amp; !f.isCancelled() &amp;&amp; !f.isDone()</code>
         */
        @Override
        public boolean isActive() {
            if (cancelled || executor.isShutdown()) {
                return false;
            }
            ScheduledFuture<?> f = future;
            return Objects.nonNull(f) && !f.isCancelled() && !f.isDone();
        }
    }
}
