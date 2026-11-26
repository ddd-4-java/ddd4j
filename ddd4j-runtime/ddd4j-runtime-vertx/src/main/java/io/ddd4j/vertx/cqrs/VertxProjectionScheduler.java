package io.ddd4j.vertx.cqrs;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.readmodel.ProjectionCron;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vert.x 原生单次 timer 驱动的投影调度器；同步持久化只在 executeBlocking worker 上执行。
 * 本实例仅拥有 timer，不拥有 Vertx 或其 worker 池。取消阻止排队/后续执行，不回滚已进入的工作。
 * CRON 保留旧数字六段、本地墙上时间、一百万秒窗口；解析委派 core。
 */
@Slf4j
public class VertxProjectionScheduler implements ViewScheduler, AutoCloseable {
    private final Vertx vertx;
    private final Set<Handle> handles = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    /**
     * 创建未调度的适配器。
     *
     * @param vertx 调用方拥有的原生实例
     */
    public VertxProjectionScheduler(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
    }

    @Override
    public synchronized ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        if (closed) {
            throw new IllegalStateException("Projection scheduler is closed");
        }
        Handle handle = new Handle(Objects.requireNonNull(viewName, "viewName must not be null"),
                CronExpression.parse(cron), Objects.requireNonNull(task, "task must not be null"),
                ThreadContext.getResources());
        handles.add(handle);
        try {
            handle.scheduleNext();
            return handle;
        } catch (RuntimeException failure) {
            handle.cancel();
            throw failure;
        }
    }

    /**
     * 取消全部自有 timer；已经执行的业务由调用方事务负责。
     */
    public synchronized void shutdown() {
        closed = true;
        handles.forEach(Handle::cancel);
        handles.clear();
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * 旧 Vert.x 数字方言与搜索窗口的兼容桥。
     */
    public static final class CronExpression {
        private final ProjectionCron delegate;

        private CronExpression(ProjectionCron delegate) {
            this.delegate = delegate;
        }

        /**
         * 解析旧六段数字表达式。
         *
         * @param expression 表达式
         * @return 共享解析器的兼容视图
         */
        public static CronExpression parse(String expression) {
            Objects.requireNonNull(expression, "expression must not be null");
            if (expression.contains("?") || expression.matches(".*[0-9]-[0-9]+/.*")) {
                throw new IllegalArgumentException("Vert.x legacy cron does not support this syntax: " + expression);
            }
            return new CronExpression(ProjectionCron.parse(expression));
        }

        /**
         * 保持本地墙上时间和原一百万秒搜索窗口。
         *
         * @param from 当前本地时间
         * @return 下一时间；窗口内无匹配返回 null
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

    private final class Handle implements ViewScheduleHandle {
        private final String name;
        private final CronExpression cron;
        private final Runnable task;
        private final Map<Object, Object> captured;
        private long timerId = -1;
        private volatile boolean cancelled;

        private Handle(String name, CronExpression cron, Runnable task, Map<Object, Object> captured) {
            this.name = name;
            this.cron = cron;
            this.task = task;
            this.captured = captured;
        }

        private synchronized void scheduleNext() {
            if (cancelled || closed) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime next = cron.next(now);
            if (Objects.isNull(next)) {
                cancel();
                return;
            }
            timerId = vertx.setTimer(Math.max(1, Duration.between(now, next).toMillis()), ignored -> dispatch());
        }

        private void dispatch() {
            if (cancelled || closed) {
                return;
            }
            vertx.executeBlocking(() -> {
                // 排队中的 worker 在开始前仍可取消；不假装中断已进入的业务事务。
                if (!cancelled && !closed) {
                    try (ThreadContext.Scope ignored = ThreadContext.open(captured)) {
                        task.run();
                    }
                }
                return null;
            }).onComplete(result -> {
                if (result.failed()) {
                    log.warn("Projection task failed: {}", name, result.cause());
                }
                scheduleNext();
            });
        }

        @Override
        public synchronized void cancel() {
            cancelled = true;
            if (timerId >= 0) {
                vertx.cancelTimer(timerId);
                timerId = -1;
            }
            handles.remove(this);
        }

        @Override
        public boolean isActive() {
            return !cancelled && !closed;
        }
    }
}
