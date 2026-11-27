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
import io.dropwizard.core.setup.Environment;

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

    /** \u5f53\u524d\u5df2\u7ecf\u6ce8\u518c\u7684\u6295\u5f71\u4efb\u52a1\u53ca\u5176\u53d6\u6d88\u53e5\u67c4\u3002 */
    private final Set<ReschedulingHandle> handles = ConcurrentHashMap.newKeySet();

    /** \u6309\u8ba1\u5212\u6267\u884c\u5f53\u524d\u7ec4\u4ef6\u540e\u53f0\u4efb\u52a1\u7684\u5b9a\u65f6\u7ebf\u7a0b\u6c60\u3002 */
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

    /** {@inheritDoc} */
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

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public synchronized void cancel() {
            cancelled = true;
            handles.remove(this);
            ScheduledFuture<?> f = future;
            if (Objects.nonNull(f)) {
                f.cancel(false);
            }
        }

        /** {@inheritDoc} */
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
