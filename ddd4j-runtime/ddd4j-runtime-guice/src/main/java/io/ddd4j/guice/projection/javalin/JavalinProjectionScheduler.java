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
package io.ddd4j.guice.projection.javalin;

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

    /**
     * 按计划执行当前组件后台任务的定时线程池。
     */
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "javalin-projection-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * 创建使用默认运行参数的 Javalin 投影调度器。
     */
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

    /**
     * 按给定时间规则安排任务。
     *
     * <p>Javalin 实现调用 <code>CronExpression.parse(cron)</code>。</p>
     *
     * @param viewName 视图名称（用于日志 / 调试）
     * @param cron     CRON 表达式
     * @param task     要执行的任务
     * @return 实际返回<code>handle</code>
     */
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

    /**
     * 旧包内测试入口保留，解析和下次时间计算委托共享规则。
     */
    static class CronExpression {
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
         * 将 Cron 文本解析为当前运行时使用的调度表达式。
         *
         * @param expression 待解析的 Cron 或表达式文本
         * @return 可计算下一次触发时间的表达式
         */
        static CronExpression parse(String expression) {
            return new CronExpression(ProjectionCron.parse(expression));
        }

        /**
         * 从给定时间之后查找下次 Cron 触发点。
         *
         * @param from 计算下一次触发的时间基准
         * @return 下一次满足表达式的时间，没有将来触发点时为空
         */
        LocalDateTime next(LocalDateTime from) {
            java.time.ZonedDateTime next = delegate.next(from.atZone(ZoneId.systemDefault()));
            return Objects.isNull(next) ? null : next.toLocalDateTime();
        }
    }
}
