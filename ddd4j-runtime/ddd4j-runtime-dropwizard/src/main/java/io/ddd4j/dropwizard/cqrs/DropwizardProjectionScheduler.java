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
package io.ddd4j.dropwizard.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionCron;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.dropwizard.setup.Environment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Dropwizard 数值 CRON 调度器；共享解析，本实例拥有 JDK 工作线程。
 * 保留旧六段数字方言、本地墙上时间及一百万秒搜索窗口。
 */
public class DropwizardProjectionScheduler implements ViewScheduler {

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
                Thread t = new Thread(r, "dropwizard-projection-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * 创建未启动线程的调度器，生命周期由使用者管理。
     */
    protected DropwizardProjectionScheduler() {
    }

    /**
     * 创建手动调度器，不向 Environment 注册 Managed。
     *
     * @param env    原生环境锚点
     * @param views  视图候选
     * @param runner 共享运行器
     * @return 未调度的实例
     */
    public static DropwizardProjectionScheduler create(Environment env,
                                                       Collection<ProjectionView<?>> views,
                                                       ProjectionRunner<?> runner) {
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(views, "views must not be null");
        Objects.requireNonNull(runner, "runner must not be null");
        return new DropwizardProjectionScheduler();
    }

    /**
     * 取消调度并中断工作线程；等待最多五秒，未退出时明确报错而非虚报释放。
     */
    public void shutdown() {
        synchronized (this) {
            handles.forEach(ReschedulingHandle::cancel);
            handles.clear();
            executor.shutdownNow();
        }
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Dropwizard projection tasks did not terminate within five seconds");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping Dropwizard projection tasks", failure);
        }
    }

    /**
     * @return 线程池是否完全停止
     */
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    /**
     * 按给定时间规则安排任务。
     *
     * <p>Dropwizard 实现调用 <code>executor.isShutdown()</code>。</p>
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
        if (executor.isShutdown()) {
            throw new RejectedExecutionException("Dropwizard projection scheduler is shut down");
        }
        CronExpression cronExpr = CronExpression.parse(cron);
        ReschedulingHandle handle = new ReschedulingHandle(executor, cronExpr, task);
        handle.scheduleNext();
        handles.add(handle);
        return handle;
    }

    /**
     * 旧 Dropwizard 六段数字语法的薄桥；墙上时间算法交给共享解析器。
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
                throw new IllegalArgumentException("Dropwizard legacy cron does not support this syntax: " + expression);
            }
            return new CronExpression(ProjectionCron.parse(expression));
        }

        /**
         * 保持原 Dropwizard 的本地墙上时间及一百万秒搜索窗口。
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
