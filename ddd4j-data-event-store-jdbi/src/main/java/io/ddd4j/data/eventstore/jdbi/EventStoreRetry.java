package io.ddd4j.data.eventstore.jdbi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * uk_position 唯一约束冲突自动重试（回填自 3.0.x 16744e1b，适配 eventstore 包路径）。
 *
 * <p>并发 append 时全局 position 由数据库唯一约束兜底，冲突按指数退避重试
 * （10/20/40/80/160ms + jitter）。
 */
final class EventStoreRetry {

    private static final Logger LOG = LoggerFactory.getLogger(EventStoreRetry.class);

    static final int DEFAULT_MAX_ATTEMPTS = 5;
    static final long BASE_DELAY_MILLIS = 10L;

    private final int maxAttempts;
    private final long baseDelayMillis;
    private final Sleeper sleeper;

    EventStoreRetry() {
        this(DEFAULT_MAX_ATTEMPTS, BASE_DELAY_MILLIS, new Sleeper() {
            @Override public void sleep(long millis) throws InterruptedException { Thread.sleep(millis); }
        });
    }

    EventStoreRetry(int maxAttempts, long baseDelayMillis, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, was " + maxAttempts);
        }
        if (baseDelayMillis < 0) {
            throw new IllegalArgumentException("baseDelayMillis must be >= 0, was " + baseDelayMillis);
        }
        this.maxAttempts = maxAttempts;
        this.baseDelayMillis = baseDelayMillis;
        this.sleeper = sleeper;
    }

    /**
     * 执行可重试操作。
     *
     * @param operation 描述（用于日志）
     * @param action    单次尝试的业务逻辑；必须抛出与 {@link #isRetriable} 匹配的异常以触发重试
     * @param <T>       返回值类型
     * @return 首次成功或最终一次尝试的返回值
     * @throws Exception 最终一次仍失败时抛出最后一次的异常
     */
    <T> T execute(String operation, RetryableAction<T> action) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.run();
            } catch (Exception e) {
                lastException = e;
                if (!isRetriable(e)) {
                    throw e;
                }
                if (attempt >= maxAttempts) {
                    LOG.warn("EventStore {} exhausted {} attempts due to retriable exception",
                            operation, maxAttempts);
                    throw e;
                }
                long delay = computeDelay(attempt);
                LOG.debug("EventStore {} attempt {}/{} failed retriably, retrying after {}ms",
                        operation, attempt, maxAttempts, delay);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("EventStore retry sleep interrupted", ie);
                }
            }
        }
        // 不应到达，但编译器要求
        throw lastException != null ? lastException : new IllegalStateException("unreachable");
    }

    static long computeDelay(int attempt) {
        long expDelay = BASE_DELAY_MILLIS << (attempt - 1);
        long jitter = ThreadLocalRandom.current().nextLong(BASE_DELAY_MILLIS + 1);
        return expDelay + jitter;
    }

    /**
     * 判定异常是否为可重试的 {@code uk_position} 冲突。
     *
     * <p>判断规则（按顺序短路）：
     * <ol>
     *   <li>{@link IllegalStateException}（乐观锁版本冲突）→ false（不可重试）</li>
     *   <li>顶层或任何 cause 为 {@link SQLIntegrityConstraintViolationException} → true</li>
     *   <li>cause 链消息含 uk_position/unique constraint/unique index/duplicate key/duplicate entry → true</li>
     *   <li>类名为 "ConstraintViolationException" 的 cause（兼容 Hibernate，避免编译期依赖） → true</li>
     * </ol>
     */
    static boolean isRetriable(Throwable t) {
        if (t == null) {
            return false;
        }
        // IllegalStateException（乐观锁版本冲突）立即抛，不重试
        if (t instanceof IllegalStateException) {
            return false;
        }
        // JDBC 唯一约束冲突标准异常（顶层或嵌套 cause）
        if (containsCause(t, SQLIntegrityConstraintViolationException.class)) {
            return true;
        }
        // 遍历 cause 链，匹配唯一约束关键字
        Throwable current = t;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("uk_position") || lower.contains("unique constraint")
                        || lower.contains("unique index") || lower.contains("duplicate key")
                        || lower.contains("duplicate entry")) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        // Hibernate ConstraintViolationException（按类名匹配，避免编译期依赖 hibernate-core）
        if (containsCauseByName(t, "ConstraintViolationException")) {
            return true;
        }
        return false;
    }

    private static boolean containsCause(Throwable t, Class<? extends Throwable> target) {
        Throwable current = t;
        while (current != null) {
            if (target.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 通过类名匹配识别 cause 链中的特定异常（避免硬依赖 Hibernate）。 */
    private static boolean containsCauseByName(Throwable t, String simpleClassName) {
        Throwable current = t;
        while (current != null) {
            if (simpleClassName.equals(current.getClass().getSimpleName())) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 单次尝试的业务逻辑。 */
    @FunctionalInterface
    interface RetryableAction<T> {
        T run() throws Exception;
    }

    /** 睡眠抽象（测试注入用）。 */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
