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

    /** \u6309\u8ba1\u5212\u6267\u884c\u5f53\u524d\u7ec4\u4ef6\u540e\u53f0\u4efb\u52a1\u7684\u5b9a\u65f6\u7ebf\u7a0b\u6c60\u3002 */
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "javalin-projection-scheduler");
                t.setDaemon(true);
                return t;
            });

    /** \u521b\u5efa\u4f7f\u7528\u9ed8\u8ba4\u8fd0\u884c\u53c2\u6570\u7684 Javalin \u6295\u5f71\u8c03\u5ea6\u5668\u3002 */
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
        public void cancel() {
            cancelled = true;
            ScheduledFuture<?> f = future;
            if (Objects.nonNull(f)) {
                f.cancel(false);
            }
        }

        /** {@inheritDoc} */
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
         * \u5c06 Cron \u6587\u672c\u89e3\u6790\u4e3a\u5f53\u524d\u8fd0\u884c\u65f6\u4f7f\u7528\u7684\u8c03\u5ea6\u8868\u8fbe\u5f0f\u3002
         * @param expression \u5f85\u89e3\u6790\u7684 Cron \u6216\u8868\u8fbe\u5f0f\u6587\u672c
         * @return \u53ef\u8ba1\u7b97\u4e0b\u4e00\u6b21\u89e6\u53d1\u65f6\u95f4\u7684\u8868\u8fbe\u5f0f
         */
        static CronExpression parse(String expression) {
            return new CronExpression(ProjectionCron.parse(expression));
        }

        /**
         * \u4ece\u7ed9\u5b9a\u65f6\u95f4\u4e4b\u540e\u67e5\u627e\u4e0b\u6b21 Cron \u89e6\u53d1\u70b9\u3002
         * @param from \u8ba1\u7b97\u4e0b\u4e00\u6b21\u89e6\u53d1\u7684\u65f6\u95f4\u57fa\u51c6
         * @return \u4e0b\u4e00\u6b21\u6ee1\u8db3\u8868\u8fbe\u5f0f\u7684\u65f6\u95f4\uff0c\u6ca1\u6709\u5c06\u6765\u89e6\u53d1\u70b9\u65f6\u4e3a\u7a7a
         */
        LocalDateTime next(LocalDateTime from) {
            java.time.ZonedDateTime next = delegate.next(from.atZone(ZoneId.systemDefault()));
            return Objects.isNull(next) ? null : next.toLocalDateTime();
        }
    }
}
