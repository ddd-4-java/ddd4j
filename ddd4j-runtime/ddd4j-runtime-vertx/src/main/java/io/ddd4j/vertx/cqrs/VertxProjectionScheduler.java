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
    /** \u5f53\u524d\u6846\u67b6\u7ec4\u4ef6\u5171\u4eab\u7684 Vert.x \u5b9e\u4f8b\u3002 */
    private final Vertx vertx;
    /** \u5f53\u524d\u5df2\u7ecf\u6ce8\u518c\u7684\u6295\u5f71\u4efb\u52a1\u53ca\u5176\u53d6\u6d88\u53e5\u67c4\u3002 */
    private final Set<Handle> handles = ConcurrentHashMap.newKeySet();
    /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
    private volatile boolean closed;

    /**
     * 创建未调度的适配器。
     *
     * @param vertx 调用方拥有的原生实例
     */
    public VertxProjectionScheduler(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void close() {
        shutdown();
    }

    /**
     * 旧 Vert.x 数字方言与搜索窗口的兼容桥。
     */
    public static final class CronExpression {
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

    /** \u7ba1\u7406 Vert.x \u6295\u5f71\u4efb\u52a1\u7684\u5b9a\u65f6\u5668\u3001Cron \u8ba1\u5212\u548c\u6355\u83b7\u7684\u4e0a\u4e0b\u6587\u3002 */
    private final class Handle implements ViewScheduleHandle {
        /** \u7528\u4e8e\u5b9a\u4f4d\u5b9a\u65f6\u4efb\u52a1\u6240\u5c5e\u89c6\u56fe\u7684\u540d\u79f0\u3002 */
        private final String name;
        /** \u7528\u6765\u786e\u5b9a\u4efb\u52a1\u4e0b\u6b21\u89e6\u53d1\u65f6\u95f4\u7684 Cron \u8868\u8fbe\u5f0f\u3002 */
        private final CronExpression cron;
        /** \u7531\u5f53\u524d\u8c03\u5ea6\u53e5\u67c4\u6267\u884c\u7684\u6295\u5f71\u4efb\u52a1\u3002 */
        private final Runnable task;
        /** \u63d0\u4ea4\u4efb\u52a1\u65f6\u6355\u83b7\u5e76\u5728\u56de\u8c03\u5185\u6062\u590d\u7684\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u3002 */
        private final Map<Object, Object> captured;
        /** \u5df2\u767b\u8bb0\u7684 Vert.x \u5b9a\u65f6\u5668\u6807\u8bc6\uff0c\u7528\u4e8e\u53d6\u6d88\u8ba1\u5212\u3002 */
        private long timerId = -1;
        /** \u4efb\u52a1\u662f\u5426\u5df2\u53d6\u6d88\u5e76\u505c\u6b62\u7ee7\u7eed\u5b89\u6392\u65b0\u7684\u89e6\u53d1\u3002 */
        private volatile boolean cancelled;

        /**
         * \u4fdd\u5b58\u4efb\u52a1\u3001Cron \u548c\u4e0a\u4e0b\u6587\u5feb\u7167\u4ee5\u4fbf\u5b9a\u65f6\u6267\u884c\u3002
         * @param name \u5f53\u524d\u7ec4\u4ef6\u3001\u6307\u6807\u3001\u4efb\u52a1\u6216 Bean \u4f7f\u7528\u7684\u540d\u79f0
         * @param cron \u4efb\u52a1\u8ba1\u5212\u7684 Cron \u8868\u8fbe\u5f0f
         * @param task \u5b9e\u9645\u6267\u884c\u4e00\u6b21\u6295\u5f71\u7684\u56de\u8c03
         * @param captured \u8c03\u5ea6\u63d0\u4ea4\u65f6\u6355\u83b7\u3001\u56de\u8c03\u65f6\u6062\u590d\u7684\u7ebf\u7a0b\u4e0a\u4e0b\u6587
         */
        private Handle(String name, CronExpression cron, Runnable task, Map<Object, Object> captured) {
            this.name = name;
            this.cron = cron;
            this.task = task;
            this.captured = captured;
        }

        /** \u6839\u636e Cron \u8ba1\u7b97\u4e0b\u4e00\u6b21\u6267\u884c\u65f6\u95f4\uff0c\u5e76\u5728\u8ba1\u5212\u4ecd\u6709\u6548\u65f6\u5411Vert.x \u5b9a\u65f6\u5668\u6ce8\u518c\u89e6\u53d1\u3002 */
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

        /** \u5728 Vert.x \u4e2d\u6d3e\u53d1\u5f53\u524d\u6295\u5f71\u56de\u8c03\u5e76\u4f7f\u7528\u6355\u83b7\u7684\u4e0a\u4e0b\u6587\u3002 */
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

        /** {@inheritDoc} */
        @Override
        public synchronized void cancel() {
            cancelled = true;
            if (timerId >= 0) {
                vertx.cancelTimer(timerId);
                timerId = -1;
            }
            handles.remove(this);
        }

        /** {@inheritDoc} */
        @Override
        public boolean isActive() {
            return !cancelled && !closed;
        }
    }
}
