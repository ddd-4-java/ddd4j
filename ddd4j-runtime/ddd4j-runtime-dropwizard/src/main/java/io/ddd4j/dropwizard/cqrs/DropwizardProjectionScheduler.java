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

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.dropwizard.core.setup.Environment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * {@link ViewScheduler} 的 Dropwizard 5 装配适配器：以静态
 * {@link #create(Environment, Collection, ProjectionRunner)} 工厂创建调度器实例，
 * 底层用 JDK {@link ScheduledExecutorService} + {@link CronExpression} 计算 cron
 * 延迟并以 {@code schedule} 注册（与 Quarkus 模式同款）。
 *
 * <p><b>不实现</b> {@code Application<Configuration>}（无 Dropwizard 生命周期绑定）；
 * 生命周期由集成方在 {@code Application.run} 内手动调 {@code create} 后触发
 * （manual-registration 模式，与 cqrs-dropwizard 的 {@code create(Application, Collection)}
 * 工厂对称）。
 *
 * <p><b>仅服务 Dropwizard 运行时</b>；Spring 系运行时用
 * {@code SpringProjectionScheduler}，Quarkus 用 {@code QuarkusProjectionScheduler}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewScheduler
 * @see DropwizardProjectionViewManager
 * @since 2.0.x
 */
public class DropwizardProjectionScheduler implements ViewScheduler {

    /** \u6309\u8ba1\u5212\u6267\u884c\u5f53\u524d\u7ec4\u4ef6\u540e\u53f0\u4efb\u52a1\u7684\u5b9a\u65f6\u7ebf\u7a0b\u6c60\u3002 */
    private final ScheduledExecutorService executor;

    /** \u521b\u5efa\u672a\u542f\u52a8\u7ebf\u7a0b\u7684\u8c03\u5ea6\u5668\uff0c\u751f\u547d\u5468\u671f\u7531\u4f7f\u7528\u8005\u7ba1\u7406\u3002 */
    private DropwizardProjectionScheduler() {
        this.executor = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "dropwizard-projection-scheduler");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * 静态装配工厂（集成方一行入口）：在 Dropwizard 应用装配期——集成方
     * {@code Application.run} 内（Jetty 启动之前）调用一次，创建调度器实例。
     *
     * <p>参数 {@code env} 为装配锚点（确保调度器绑定真实 Dropwizard 应用环境创建），
     * {@code views} 和 {@code runner} 供后续 {@link DropwizardProjectionViewManager}
     * 使用——本工厂只创建调度器，view 注册/管理由 ViewManager 负责。
     *
     * @param env    Dropwizard 环境（装配锚点），非空
     * @param views  集成方供入的投影视图集合（本工厂不消费，仅传递），非空
     * @param runner 投影运行器（本工厂不消费，仅传递），非空
     * @return 调度器实例
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
     * 关闭调度器（释放线程池资源）。
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 按给定时间规则安排任务。
     *
     * <p>Dropwizard 实现调用 <code>CronExpression.parse(cron)</code>。</p>
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
            if (next == null) {
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
            if (f != null) {
                f.cancel(false);
            }
        }

        /**
         * 检查当前句柄是否仍处于有效状态。
         *
         * <p>此实现调用 <code>f.isCancelled()</code>。</p>
         *
         * @return 根据执行路径返回<code>false</code>、<code>f != null &amp;&amp; !f.isCancelled() &amp;&amp; !f.isDone()</code>
         */
        @Override
        public boolean isActive() {
            if (cancelled) {
                return false;
            }
            ScheduledFuture<?> f = future;
            return f != null && !f.isCancelled() && !f.isDone();
        }
    }

    // ======== Minimal CRON parser (6-field: sec min hour dom month dow) ========

    /**
     * Minimal 6-field CRON parser (sec min hour dom month dow).
     *
     * <p>Supports: wildcard, range, step, comma list, and literal values.
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
         * \u4fdd\u6301\u539f Dropwizard \u7684\u672c\u5730\u5899\u4e0a\u65f6\u95f4\u53ca\u4e00\u767e\u4e07\u79d2\u641c\u7d22\u7a97\u53e3\u3002
         * @param from \u672c\u5730\u65f6\u95f4
         * @return \u4e0b\u4e00\u6267\u884c\u65f6\u95f4\uff0c\u7a97\u53e3\u5185\u6ca1\u6709\u5339\u914d\u8fd4\u56de null
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
