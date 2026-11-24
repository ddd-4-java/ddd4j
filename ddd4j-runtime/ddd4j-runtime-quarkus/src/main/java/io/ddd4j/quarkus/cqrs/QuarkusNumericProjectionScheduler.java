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

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.core.cqrs.readmodel.ProjectionCron;
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

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "quarkus-projection-scheduler");
                t.setDaemon(true);
                return t;
            });

    /** 关闭本适配器拥有的数值 cron 线程池。 */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

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

        private final ScheduledExecutorService executor;
        private final ProjectionCron cron;
        private final Runnable task;
        private volatile ScheduledFuture<?> future;
        private volatile boolean cancelled = false;

        ReschedulingHandle(ScheduledExecutorService executor, ProjectionCron cron, Runnable task) {
            this.executor = executor;
            this.cron = cron;
            this.task = task;
        }

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
        public void cancel() {
            cancelled = true;
            ScheduledFuture<?> f = future;
            if (Objects.nonNull(f)) {
                f.cancel(false);
            }
        }

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
