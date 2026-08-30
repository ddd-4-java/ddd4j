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
package io.ddd4j.data.event.store.panache;

import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Panache EventStore 重试工具：捕获 {@code uk_position} 唯一约束冲突后
 * 按指数退避自动重试整个 append 操作（保持 {@code expectedVersion} 不变）。
 *
 * <p>检测范围（按顺序短路）：
 * <ol>
 *   <li>{@link IllegalStateException}（乐观锁版本冲突）— 立即抛，不重试</li>
 *   <li>{@link PersistenceException} 嵌套 {@link ConstraintViolationException} 或 {@link SQLException}</li>
 *   <li>任何 cause 链中的 {@link SQLIntegrityConstraintViolationException}</li>
 *   <li>消息含 "uk_position"/"unique constraint"/"duplicate key"/"duplicate entry" 的 SQLException</li>
 * </ol>
 *
 * <p>退避：基础 10ms，指数翻倍 + 0-10ms 抖动。默认最多 5 次尝试，总耗时 ~310ms 上限。
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
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (baseDelayMillis < 0) {
            throw new IllegalArgumentException("baseDelayMillis must be >= 0");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelayMillis = baseDelayMillis;
        this.sleeper = sleeper;
    }

    /**
     * 执行可重试操作。
     *
     * @param operation 操作描述（用于日志）
     * @param action    单次尝试逻辑
     */
    void execute(String operation, RetryableAction action) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return;
            } catch (Exception raw) {
                RuntimeException e = unwrapToRuntime(raw);
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
        throw lastException != null ? lastException : new IllegalStateException("unreachable");
    }

    /**
     * 拆包 checked 异常：如果异常本身是 RuntimeException 或 Error 直接返回；否则包装。
     * 包装时优先保留 cause（用于 isRetriable 遍历 cause 链）。
     */
    private static RuntimeException unwrapToRuntime(Exception raw) {
        if (raw instanceof RuntimeException) {
            return (RuntimeException) raw;
        }
        return new RuntimeException(raw);
    }

    static long computeDelay(int attempt) {
        long expDelay = BASE_DELAY_MILLIS << (attempt - 1);
        long jitter = ThreadLocalRandom.current().nextLong(BASE_DELAY_MILLIS + 1);
        return expDelay + jitter;
    }

    /**
     * 判定异常是否为可重试的 {@code uk_position} 冲突。
     */
    static boolean isRetriable(Throwable t) {
        if (t == null) {
            return false;
        }
        // 乐观锁失败信号：立即抛
        if (t instanceof IllegalStateException) {
            return false;
        }
        // 嵌套 SQLIntegrityConstraintViolationException
        if (containsCause(t, SQLIntegrityConstraintViolationException.class)) {
            return true;
        }
        // JPA PersistenceException + message 含 uk_position（覆盖 Hibernate 包装路径）
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
            // PersistenceException 一般不带 SQL 关键字，但其 cause 链会包含；
            // 上面 SQLException 遍历已覆盖。
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        // PersistenceException 单独识别（Hibernate 唯一约束会包成 PersistenceException，
        // cause 链未必有 SQLException 但有 ConstraintViolationException——后者是
        // org.hibernate.exception 的，编译期不可见，故此处仅按类名匹配）
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

    /**
     * 通过类名匹配识别 cause 链中的特定异常（避免硬依赖 Hibernate）。
     *
     * <p>用于检测 {@code org.hibernate.exception.ConstraintViolationException}——它在
     * Hibernate 唯一约束冲突时由 JPA {@code PersistenceException} 包装，但模块的
     * compile classpath 不含 hibernate-core（在 test scope）。运行时的 hibernate-core
     * 来自 {@code quarkus-hibernate-orm-panache} 传递依赖。
     */
    private static boolean containsCauseByName(Throwable t, String simpleClassName) {
        Throwable current = t;
        while (current != null) {
            if (current.getClass().getSimpleName().equals(simpleClassName)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    @FunctionalInterface
    interface RetryableAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
