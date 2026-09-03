package io.ddd4j.data.projection.quarkus;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.LocalDateTime;
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
 * {@link #schedule} 用 {@link CronExpression} 解析 cron 得到下次执行延迟，
 * 以 {@code scheduleWithFixedDelay} 注册并自动重调度。
 *
 * <p><b>仅服务 Quarkus 运行时</b>；Spring 系运行时用
 * {@code SpringProjectionScheduler}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewScheduler
 * @since 2.0.x
 */
@ApplicationScoped
public class QuarkusProjectionScheduler implements ViewScheduler {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "quarkus-projection-scheduler");
                t.setDaemon(true);
                return t;
            });

    @PreDestroy
    void shutdown() {
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
     * <p>Supports: wildcard (*), range (1-5), step ({@code &#42;/5}),
     * comma list (1,3,5), and literal values.
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
