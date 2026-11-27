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
    /**
     * 当前框架组件共享的 Vert.x 实例。
     */
    private final Vertx vertx;
    /**
     * 当前已经注册的投影任务及其取消句柄。
     */
    private final Set<Handle> handles = ConcurrentHashMap.newKeySet();
    /**
     * 当前实例是否已关闭。
     */
    private volatile boolean closed;

    /**
     * 创建未调度的适配器。
     *
     * @param vertx 调用方拥有的原生实例
     */
    public VertxProjectionScheduler(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
    }

    /**
     * 按给定时间规则安排任务。
     *
     * <p>满足 <code>closed</code> 时抛出 <code>IllegalStateException</code>。 Vert.x 实现调用 <code>CronExpression.parse(cron)</code>。</p>
     *
     * @param viewName 视图名称（用于日志 / 调试）
     * @param cron     CRON 表达式
     * @param task     要执行的任务
     * @return 实际返回<code>handle</code>
     */
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

    /**
     * 结束当前组件持有的资源。
     *
     * <p>Vert.x 实现调用 <code>shutdown()</code>。</p>
     */
    @Override
    public void close() {
        shutdown();
    }

    /**
     * 旧 Vert.x 数字方言与搜索窗口的兼容桥。
     */
    public static final class CronExpression {
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

    /**
     * 管理 Vert.x 投影任务的定时器、Cron 计划和捕获的上下文。
     */
    private final class Handle implements ViewScheduleHandle {
        /**
         * 用于定位定时任务所属视图的名称。
         */
        private final String name;
        /**
         * 用来确定任务下次触发时间的 Cron 表达式。
         */
        private final CronExpression cron;
        /**
         * 由当前调度句柄执行的投影任务。
         */
        private final Runnable task;
        /**
         * 提交任务时捕获并在回调内恢复的线程上下文。
         */
        private final Map<Object, Object> captured;
        /**
         * 已登记的 Vert.x 定时器标识，用于取消计划。
         */
        private long timerId = -1;
        /**
         * 任务是否已取消并停止继续安排新的触发。
         */
        private volatile boolean cancelled;

        /**
         * 保存任务、Cron 和上下文快照以便定时执行。
         *
         * @param name     当前组件、指标、任务或 Bean 使用的名称
         * @param cron     任务计划的 Cron 表达式
         * @param task     实际执行一次投影的回调
         * @param captured 调度提交时捕获、回调时恢复的线程上下文
         */
        private Handle(String name, CronExpression cron, Runnable task, Map<Object, Object> captured) {
            this.name = name;
            this.cron = cron;
            this.task = task;
            this.captured = captured;
        }

        /**
         * 根据 Cron 计算下一次执行时间，并在计划仍有效时向Vert.x 定时器注册触发。
         */
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

        /**
         * 在 Vert.x 中派发当前投影回调并使用捕获的上下文。
         */
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

        /**
         * 撤销当前任务句柄允许撤销的操作。
         *
         * <p>此实现调用 <code>vertx.cancelTimer(timerId)</code>。</p>
         */
        @Override
        public synchronized void cancel() {
            cancelled = true;
            if (timerId >= 0) {
                vertx.cancelTimer(timerId);
                timerId = -1;
            }
            handles.remove(this);
        }

        /**
         * 检查当前句柄是否仍处于有效状态。
         *
         * @return 实际返回<code>!cancelled &amp;&amp; !closed</code>，作为本次判断结果
         */
        @Override
        public boolean isActive() {
            return !cancelled && !closed;
        }
    }
}
