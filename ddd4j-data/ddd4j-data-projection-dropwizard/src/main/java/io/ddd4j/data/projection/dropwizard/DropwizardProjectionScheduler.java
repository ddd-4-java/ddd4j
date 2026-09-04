package io.ddd4j.data.projection.dropwizard;

import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.dropwizard.setup.Environment;

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

    private final ScheduledExecutorService executor;

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
            if (next == null) {
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
            if (f != null) {
                f.cancel(false);
            }
        }

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

        private final int[] seconds;
        private final int[] minutes;
        private final int[] hours;
        private final int[] daysOfMonth;
        private final int[] months;
        private final int[] daysOfWeek;

        private CronExpression(int[] seconds, int[] minutes, int[] hours,
                               int[] daysOfMonth, int[] months, int[] daysOfWeek) {
            this.seconds = seconds;
            this.minutes = minutes;
            this.hours = hours;
            this.daysOfMonth = daysOfMonth;
            this.months = months;
            this.daysOfWeek = daysOfWeek;
        }

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

        private boolean matches(LocalDateTime dt) {
            return contains(seconds, dt.getSecond())
                    && contains(minutes, dt.getMinute())
                    && contains(hours, dt.getHour())
                    && contains(daysOfMonth, dt.getDayOfMonth())
                    && contains(months, dt.getMonthValue())
                    && matchesDayOfWeek(dt);
        }

        private boolean matchesDayOfWeek(LocalDateTime dt) {
            int dow = dt.getDayOfWeek().getValue() % 7; // Monday=1 -> 1, Sunday=7 -> 0
            return contains(daysOfWeek, dow) || contains(daysOfWeek, dt.getDayOfWeek().getValue());
        }

        private static boolean contains(int[] values, int v) {
            for (int val : values) {
                if (val == v) {
                    return true;
                }
            }
            return false;
        }

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
