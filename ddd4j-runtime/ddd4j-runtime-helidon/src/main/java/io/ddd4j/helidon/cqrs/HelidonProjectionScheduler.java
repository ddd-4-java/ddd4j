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
public class HelidonProjectionScheduler implements ViewScheduler {

    /** \u5f53\u524d\u5df2\u7ecf\u6ce8\u518c\u7684\u6295\u5f71\u4efb\u52a1\u53ca\u5176\u53d6\u6d88\u53e5\u67c4\u3002 */
    private final Set<ReschedulingHandle> handles = ConcurrentHashMap.newKeySet();

    /** \u6309\u8ba1\u5212\u6267\u884c\u5f53\u524d\u7ec4\u4ef6\u540e\u53f0\u4efb\u52a1\u7684\u5b9a\u65f6\u7ebf\u7a0b\u6c60\u3002 */
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
     * @param cron CRON 表达式
     * @param task 要执行的任务
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
