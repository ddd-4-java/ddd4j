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
package io.ddd4j.vertx.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.vertx.core.Vertx;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vert.x 5 投影调度器（{@link ViewScheduler} SPI 的 Vert.x 实现）。
 *
 * <p>Vert.x 无 DI 容器——以静态 {@link #create} 工厂装配（与
 * {@code VertxCommandBus} 同款哲学），无 {@code @Inject} / {@code @PreDestroy}
 * 注解。JDK {@link java.util.concurrent.ScheduledExecutorService} 兜底（Vert.x
 * 无内置 cron 调度）：{@link #schedule} 用 {@link CronExpression} 解析 cron 得到
 * 下次执行延迟，以 {@code schedule} 单次注册 + 自动重调度（与 Quarkus/Helidon/Javalin
 * 模式同款）。
 *
 * <p><b>生命周期</b>：集成方需在 Vert.x 实例关闭时手动调用 {@link #shutdown()}
 * （释放线程池资源）。典型装配模式：
 * <pre>{@code
 * VertxProjectionScheduler scheduler = VertxProjectionScheduler.create(vertx, views, runner);
 * // ... 使用 scheduler
 * // 应用关闭时：
 * scheduler.shutdown();
 * }</pre>
 *
 * <p><b>仅服务 Vert.x 运行时</b>；Spring 系运行时用
 * {@code SpringProjectionScheduler}，Quarkus 用 {@code QuarkusProjectionScheduler}，
 * Helidon SE 用 {@code HelidonProjectionScheduler}，Javalin 用
 * {@code JavalinProjectionScheduler}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewScheduler
 * @see VertxProjectionViewManager
 * @since 2.0.x
 */
public class VertxProjectionScheduler implements ViewScheduler {

    /** 执行延迟投影任务的调度线程池。 */
    private final java.util.concurrent.ScheduledExecutorService executor =
            java.util.concurrent.Executors.newScheduledThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    r -> {
                        Thread t = new Thread(r, "vertx-projection-scheduler");
                        t.setDaemon(true);
                        return t;
                    });

    /** 初始化 Vert.x 投影调度器使用的执行环境。 */
    private VertxProjectionScheduler() {
        // 私有构造（静态工厂收口装配）
    }

    /**
     * 静态装配工厂（集成方一行入口）：创建调度器。
     *
     * <p>须在 Vert.x 应用装配期调用——{@code vertx} 参数即装配锚点（强制调度器在真实
     * Vert.x 实例的存在下创建，拒绝脱离运行时的空中装配）。{@code views} 和
     * {@code runner} 参数本调度器不直接使用，供 API 一致性与
     * {@link VertxProjectionViewManager#create} 同签名。
     *
     * @param vertx  集成方 Vert.x 实例（装配锚点，经 {@code Vertx.vertx()} 获取），非空
     * @param views  投影视图集合（本调度器不直接使用，供 API 一致性），非空
     * @param runner 投影运行器（本调度器不直接使用，供 API 一致性），非空
     * @return 调度器实例
     */
    public static VertxProjectionScheduler create(Vertx vertx,
                                                  Collection<ProjectionView<?>> views,
                                                  ProjectionRunner<?> runner) {
        Objects.requireNonNull(vertx, "vertx must not be null");
        Objects.requireNonNull(views, "views must not be null");
        Objects.requireNonNull(runner, "runner must not be null");
        return new VertxProjectionScheduler();
    }

    /**
     * 关闭调度器（释放线程池资源）。
     *
     * <p>集成方需在 Vert.x 实例关闭时手动调用本方法。
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 按给定时间规则安排任务。
     *
     * <p>Vert.x 实现调用 <code>CronExpression.parse(cron)</code>。</p>
     *
     * @param viewName 视图名称（用于日志 / 调试）
     * @param cron CRON 表达式
     * @param task 要执行的任务
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

        /** 执行延迟投影任务的调度线程池。 */
        private final java.util.concurrent.ScheduledExecutorService executor;
        /** 控制投影任务执行时点的 Cron 表达式。 */
        private final CronExpression cron;
        /** 到达计划时点后执行的投影任务。 */
        private final Runnable task;
        /** 下一次投影任务对应的定时执行句柄。 */
        private volatile java.util.concurrent.ScheduledFuture<?> future;
        /** 当前任务是否已被取消。 */
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        /**
         * 保留执行器、Cron 规则以及每次触发时需要运行的任务。
         *
         * @param executor 执行延迟投影任务的线程池
         * @param cron 控制任务触发时刻的 Cron 表达式
         * @param task 计划到期时调用的投影任务
         */
        ReschedulingHandle(java.util.concurrent.ScheduledExecutorService executor,
                           CronExpression cron, Runnable task) {
            this.executor = executor;
            this.cron = cron;
            this.task = task;
        }

        /** 在句柄仍有效时计算下一触发时点并向线程池提交延迟任务。 */
        void scheduleNext() {
            if (cancelled.get()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime next = cron.next(now);
            if (next == null) {
                return;
            }
            long delayMillis = Duration.between(now, next).toMillis();
            if (delayMillis < 0) {
                delayMillis = 0;
            }
            future = executor.schedule(this, delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        /** 执行一次投影任务，并在任务完成后安排下一触发时点。 */
        @Override
        public void run() {
            if (cancelled.get()) {
                return;
            }
            try {
                task.run();
            } finally {
                scheduleNext();
            }
        }

        /** 标记句柄取消并撤销待执行的任务。 */
        @Override
        public void cancel() {
            cancelled.set(true);
            java.util.concurrent.ScheduledFuture<?> f = future;
            if (f != null) {
                f.cancel(false);
            }
        }

        /**
         * 检查句柄是否仍有待执行的定时任务。
         *
         * @return 是否存在尚未完成且未取消的任务
         */
        @Override
        public boolean isActive() {
            if (cancelled.get()) {
                return false;
            }
            java.util.concurrent.ScheduledFuture<?> f = future;
            return f != null && !f.isCancelled() && !f.isDone();
        }
    }

    // ======== Minimal CRON parser (6-field: sec min hour dom month dow) ========

    /**
     * Minimal 6-field CRON parser (sec min hour dom month dow).
     *
     * <p>Supports: wildcard (*), range (1-5), step ({@code &#42;/5}),
     * comma list (1,3,5), and literal values.
     * Does not support L/W/C special characters.
     */
    static class CronExpression {

        /** Cron 秒字段允许的取值。 */
        private final int[] seconds;
        /** Cron 分字段允许的取值。 */
        private final int[] minutes;
        /** Cron 小时字段允许的取值。 */
        private final int[] hours;
        /** Cron 月内日期字段允许的取值。 */
        private final int[] daysOfMonth;
        /** Cron 月字段允许的取值。 */
        private final int[] months;
        /** Cron 星期字段允许的取值。 */
        private final int[] daysOfWeek;

        /**
         * 以各 Cron 字段允许的取值集合构造表达式。
         *
         * @param seconds Cron 秒字段允许的取值
         * @param minutes Cron 分字段允许的取值
         * @param hours Cron 小时字段允许的取值
         * @param daysOfMonth Cron 月内日期字段允许的取值
         * @param months Cron 月字段允许的取值
         * @param daysOfWeek Cron 星期字段允许的取值
         */
        private CronExpression(int[] seconds, int[] minutes, int[] hours,
                               int[] daysOfMonth, int[] months, int[] daysOfWeek) {
            this.seconds = seconds;
            this.minutes = minutes;
            this.hours = hours;
            this.daysOfMonth = daysOfMonth;
            this.months = months;
            this.daysOfWeek = daysOfWeek;
        }

        /**
         * 解析按秒、分、时、日、月、星期排列的六段 Cron 表达式。
         *
         * @param expr 待解析的六字段 Cron 表达式文本
         * @return 已按六个字段解析的 Cron 表达式
         */
        static CronExpression parse(String expr) {
            String[] parts = expr.trim().split("\\s+");
            if (parts.length != 6) {
                throw new IllegalArgumentException("CRON expression must have 6 fields (sec min hour dom month dow): " + expr);
            }
            return new CronExpression(
                    parseField(parts[0], 0, 59),
                    parseField(parts[1], 0, 59),
                    parseField(parts[2], 0, 23),
                    parseField(parts[3], 1, 31),
                    parseField(parts[4], 1, 12),
                    parseField(parts[5], 0, 7)
            );
        }

        /**
         * \u4fdd\u6301\u672c\u5730\u5899\u4e0a\u65f6\u95f4\u548c\u539f\u4e00\u767e\u4e07\u79d2\u641c\u7d22\u7a97\u53e3\u3002
         * @param from \u5f53\u524d\u672c\u5730\u65f6\u95f4
         * @return \u4e0b\u4e00\u65f6\u95f4\uff1b\u7a97\u53e3\u5185\u65e0\u5339\u914d\u8fd4\u56de null
         */
        LocalDateTime next(LocalDateTime from) {
            LocalDateTime candidate = from.plusSeconds(1).withNano(0);
            for (int i = 0; i < 1_000_000; i++) {
                if (matches(candidate)) {
                    return candidate;
                }
                candidate = candidate.plusSeconds(1);
            }
            return null;
        }

        /**
         * 检查日期时间是否符合当前 Cron 表达式。
         *
         * @param dt 传入此操作的 <code>LocalDateTime</code> 类型 <code>dt</code>
         * @return 当前时间是否满足全部 Cron 条件
         */
        private boolean matches(LocalDateTime dt) {
            return contains(seconds, dt.getSecond())
                    && contains(minutes, dt.getMinute())
                    && contains(hours, dt.getHour())
                    && contains(daysOfMonth, dt.getDayOfMonth())
                    && contains(months, dt.getMonthValue())
                    && matchesDayOfWeek(dt);
        }

        /**
         * 按 Cron 星期编号检查给定时间，兼容星期日的两种表示。
         *
         * @param dt 传入此操作的 <code>LocalDateTime</code> 类型 <code>dt</code>
         * @return 当前时间是否符合星期字段
         */
        private boolean matchesDayOfWeek(LocalDateTime dt) {
            int dow = dt.getDayOfWeek().getValue() % 7; // Monday=1 -> 1, Sunday=7 -> 0
            return contains(daysOfWeek, dow) || contains(daysOfWeek, dt.getDayOfWeek().getValue());
        }

        /**
         * 查找指定数值是否属于当前 Cron 字段允许的集合。
         *
         * @param values 查找指定值时使用的候选集合
         * @param v 待检查是否存在的候选值
         * @return 给定值是否在候选集合中
         */
        private static boolean contains(int[] values, int v) {
            for (int val : values) {
                if (val == v) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 展开 Cron 字段的通配符、列表、区间和步长，校验数值边界。
         *
         * @param field 当前待展开的 Cron 字段
         * @param min 此字段允许的最小数值
         * @param max 此字段允许的最大数值
         * @return 此字段允许的数值集合
         */
        private static int[] parseField(String field, int min, int max) {
            if ("*".equals(field)) {
                int[] result = new int[max - min + 1];
                for (int i = 0; i < result.length; i++) {
                    result[i] = min + i;
                }
                return result;
            }
            java.util.List<Integer> values = new java.util.ArrayList<>();
            for (String part : field.split(",")) {
                if (part.contains("/")) {
                    String[] stepParts = part.split("/", 2);
                    int step = Integer.parseInt(stepParts[1]);
                    int start = "*".equals(stepParts[0]) ? min : Integer.parseInt(stepParts[0]);
                    for (int v = start; v <= max; v += step) {
                        values.add(v);
                    }
                } else if (part.contains("-")) {
                    String[] rangeParts = part.split("-", 2);
                    int from = Integer.parseInt(rangeParts[0]);
                    int to = Integer.parseInt(rangeParts[1]);
                    for (int v = from; v <= to; v++) {
                        values.add(v);
                    }
                } else {
                    values.add(Integer.parseInt(part));
                }
            }
            return values.stream().mapToInt(Integer::intValue).toArray();
        }
    }
}
