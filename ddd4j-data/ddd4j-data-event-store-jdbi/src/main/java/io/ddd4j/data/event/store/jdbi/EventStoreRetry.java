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
package io.ddd4j.data.event.store.jdbi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EventStore append 重试工具：捕获 {@code uk_position} 唯一约束冲突后
 * 按指数退避自动重试整个 append 操作（保持 {@code expectedVersion} 不变）。
 *
 * <p>仅对{@link #isRetriable(Throwable) 可重试异常}做重试：
 * <ul>
 *   <li>{@link SQLIntegrityConstraintViolationException}（JDBC 标准）</li>
 *   <li>任意 {@link SQLException} 含 "uk_position" 或 "unique constraint" 字样</li>
 * </ul>
 *
 * <p>非可重试异常（{@link IllegalStateException} 乐观锁失败等）立即抛出，不重试。
 *
 * <p>退避策略：基础延迟 10ms，每次翻倍，加 0-10ms 抖动避免雷鸣群。
 * 默认最大 5 次尝试（含首次）；总耗时上限 ~310ms（10+20+40+80+160+抖动）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
final class EventStoreRetry {

    private static final Logger LOG = LoggerFactory.getLogger(EventStoreRetry.class);

    static final int DEFAULT_MAX_ATTEMPTS = 5;
    static final long BASE_DELAY_MILLIS = 10L;

    private final int maxAttempts;
    private final long baseDelayMillis;
    private final Sleeper sleeper;

    EventStoreRetry() {
        this(DEFAULT_MAX_ATTEMPTS, BASE_DELAY_MILLIS, Thread::sleep);
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
     * @throws Exception 最终一次仍失败时抛出最后一次的异常（若为可重试异常则耗尽重试）
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
                sleeper.sleep(delay);
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
     *   <li>非 {@link RuntimeException} 且非 {@link Exception}（如 {@link java.lang.Error}）→ false</li>
     *   <li>顶层异常为 {@link SQLIntegrityConstraintViolationException} → true</li>
     *   <li>顶层或任何 cause 为 {@link SQLException}，且 message 含 "uk_position"/"unique constraint" → true</li>
     *   <li>{@link IllegalStateException} 是乐观锁失败信号，<b>不可</b>重试</li>
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
        // JDBC 唯一约束冲突标准异常
        if (t instanceof SQLIntegrityConstraintViolationException) {
            return true;
        }
        // 任意 SQLException 嵌套链中匹配关键字
        Throwable current = t;
        while (current != null) {
            if (current instanceof SQLException) {
                String msg = current.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase();
                    if (lower.contains("uk_position") || lower.contains("unique constraint")
                            || lower.contains("duplicate key") || lower.contains("duplicate entry")) {
                        return true;
                    }
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 单次尝试的业务逻辑。
     */
    @FunctionalInterface
    interface RetryableAction<T> {
        T run() throws Exception;
    }

    /**
     * 抽象 {@code Thread.sleep} 以便测试不真正等待。
     */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
