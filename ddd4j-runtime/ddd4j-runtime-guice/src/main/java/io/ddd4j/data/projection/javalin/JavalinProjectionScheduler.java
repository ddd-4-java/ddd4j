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
package io.ddd4j.data.projection.javalin;

import io.ddd4j.core.cqrs.readmodel.ProjectionCron;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.javalin.Javalin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Javalin 投影调度器（{@link ViewScheduler} SPI 的 Javalin 实现）。
 *
 * <p>Javalin 无 DI 容器——以静态 {@link #create} 工厂装配（与
 * {@code JavalinCommandBus} 同款哲学），无 {@code @Inject} / {@code @PreDestroy}
 * 注解。JDK {@link ScheduledExecutorService} 兜底（Javalin 无内置 cron 调度）：
 * {@link #schedule} 用 {@link CronExpression} 解析 cron 得到下次执行延迟，
 * 以 {@code schedule} 单次注册 + 自动重调度（与 Quarkus/Helidon 模式同款）。
 *
 * <p><b>生命周期</b>：Javalin 7.x 的生命周期事件在 {@code Javalin.create(config ->
 * config.events.serverStopping(...))} 装配期内配置，不在已创建实例上后置注册——集成方需在
 * {@code Javalin.create} 装配期内经 {@code config.events.serverStopping(scheduler::shutdown)} 注册关闭钩子，或在应用关闭时
 * 手动调用 {@link #shutdown()}。典型装配模式：
 * <pre>{@code
 * JavalinProjectionScheduler[] holder = new JavalinProjectionScheduler[1];
 * Javalin app = Javalin.create(config -> {
 *     // ... 其它配置
 *     config.events.serverStopping(() -> holder[0].shutdown());
 * });
 * holder[0] = JavalinProjectionScheduler.create(app, views, runner);
 * }</pre>
 *
 * <p><b>仅服务 Javalin 运行时</b>；Spring 系运行时用
 * {@code SpringProjectionScheduler}，Quarkus 用 {@code QuarkusProjectionScheduler}，
 * Helidon SE 用 {@code HelidonProjectionScheduler}，Vert.x 用
 * {@code VertxProjectionScheduler}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewScheduler
 * @see JavalinProjectionViewManager
 * @since 2.0.x
 */
public class JavalinProjectionScheduler implements ViewScheduler {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "javalin-projection-scheduler");
                t.setDaemon(true);
                return t;
            });

    private JavalinProjectionScheduler() {
        // 私有构造（静态工厂收口装配）
    }

    /**
     * 静态装配工厂（集成方一行入口）：创建调度器。
     *
     * <p>须在 Javalin 应用装配期调用——{@code app} 参数即装配锚点（强制调度器在真实
     * Javalin 应用实例的存在下创建，拒绝脱离应用的空中装配）。{@code views} 和
     * {@code runner} 参数本调度器不直接使用，供 API 一致性与
     * {@link JavalinProjectionViewManager#create} 同签名。
     *
     * <p><b>生命周期注意</b>：Javalin 7.x 的生命周期事件在 {@code Javalin.create}
     * 装配期内配置，不在已创建实例上后置注册——集成方需在 {@code Javalin.create} 装配期内
     * 经 {@code config.events.serverStopping(scheduler::shutdown)}
     * 注册关闭钩子，或在应用关闭时手动调用 {@link #shutdown()}。
     *
     * @param app    集成方 Javalin 应用实例（装配锚点），非空
     * @param views  投影视图集合（本调度器不直接使用，供 API 一致性），非空
     * @param runner 投影运行器（本调度器不直接使用，供 API 一致性），非空
     * @return 调度器实例
     */
    public static JavalinProjectionScheduler create(Javalin app,
                                                    Collection<ProjectionView<?>> views,
                                                    ProjectionRunner<?> runner) {
        Objects.requireNonNull(app, "app must not be null");
        Objects.requireNonNull(views, "views must not be null");
        Objects.requireNonNull(runner, "runner must not be null");
        return new JavalinProjectionScheduler();
    }

    /**
     * 关闭调度器（释放线程池资源）。
     *
     * <p>Javalin 7.x 模式：集成方需在 {@code Javalin.create} 装配期内经
     * {@code config.events.serverStopping(scheduler::shutdown)}
     * 注册关闭钩子，或在应用关闭时手动调用本方法。
     */
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

        ReschedulingHandle(ScheduledExecutorService executor, CronExpression cron, Runnable task) {
            this.executor = executor;
            this.cron = cron;
            this.task = task;
        }

        void scheduleNext() {
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

    /**
     * 旧包内测试入口保留，解析和下次时间计算委托共享规则。
     */
    static class CronExpression {
        private final ProjectionCron delegate;

        private CronExpression(ProjectionCron delegate) {
            this.delegate = delegate;
        }

        static CronExpression parse(String expression) {
            return new CronExpression(ProjectionCron.parse(expression));
        }

        LocalDateTime next(LocalDateTime from) {
            java.time.ZonedDateTime next = delegate.next(from.atZone(ZoneId.systemDefault()));
            return Objects.isNull(next) ? null : next.toLocalDateTime();
        }
    }
}
