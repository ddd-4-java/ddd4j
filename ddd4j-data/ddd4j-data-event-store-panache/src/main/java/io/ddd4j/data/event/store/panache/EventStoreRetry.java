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
 *   <li>{@link PersistenceException} 嵌套 {@link org.hibernate.exception.ConstraintViolationException} 或 {@link SQLException}</li>
 *   <li>任何 cause 链中的 {@link SQLIntegrityConstraintViolationException}</li>
 *   <li>消息含 "uk_position"/"unique constraint"/"duplicate key"/"duplicate entry" 的 SQLException</li>
 * </ol>
 *
 * <p>退避：基础 10ms，指数翻倍 + 0-10ms 抖动。默认最多 5 次尝试，总耗时 ~310ms 上限。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
final class EventStoreRetry {

    /**
     * 重试策略允许执行操作的默认最大次数。
     */
    static final int DEFAULT_MAX_ATTEMPTS = 5;
    /**
     * 指数退避及随机抖动使用的基准毫秒数。
     */
    static final long BASE_DELAY_MILLIS = 10L;
    /**
     * 用于记录事件存储重试和失败的日志记录器。
     */
    private static final Logger LOG = LoggerFactory.getLogger(EventStoreRetry.class);
    /**
     * 当前策略允许执行一次事件写入的最多尝试次数。
     */
    private final int maxAttempts;
    /**
     * 重试相邻尝试之间的基础延时，单位为毫秒。
     */
    private final long baseDelayMillis;
    /**
     * 在两次重试之间执行等待的函数。
     */
    private final Sleeper sleeper;

    /**
     * 以默认尝试次数、退避配置和线程等待行为装配重试策略。
     */
    EventStoreRetry() {
        this(DEFAULT_MAX_ATTEMPTS, BASE_DELAY_MILLIS, Thread::sleep);
    }

    /**
     * 按给定次数和等待策略创建事件追加重试逻辑。
     *
     * @param maxAttempts     操作最多允许尝试的次数
     * @param baseDelayMillis 计算退避的基础毫秒数
     * @param sleeper         执行重试等待的组件
     */
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
     * 拆包 checked 异常：如果异常本身是 RuntimeException 或 Error 直接返回；否则包装。
     * 包装时优先保留 cause（用于 isRetriable 遍历 cause 链）。
     *
     * @param raw 底层操作抛出的原始异常
     * @return 解包后的运行时异常，必要时包装原异常
     */
    private static RuntimeException unwrapToRuntime(Exception raw) {
        if (raw instanceof RuntimeException) {
            return (RuntimeException) raw;
        }
        return new RuntimeException(raw);
    }

    /**
     * 将按尝试次数增长的指数退避与随机抖动相加。
     *
     * @param attempt 当前执行失败后用于计算退避的尝试编号，从 1 开始
     * @return 下次尝试前等待的毫秒数
     */
    static long computeDelay(int attempt) {
        long expDelay = BASE_DELAY_MILLIS << (attempt - 1);
        long jitter = ThreadLocalRandom.current().nextLong(BASE_DELAY_MILLIS + 1);
        return expDelay + jitter;
    }

    /**
     * 判定异常是否为可重试的 {@code uk_position} 冲突。
     *
     * @param t 检查是否可重试的异常
     * @return 异常链包含允许重试的异常时返回 true
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
        // 遍历 cause 链，匹配两种关键字：
        //   1. SQLException 嵌套中的标准 SQL 关键字
        //   2. 任何 RuntimeException（含 PersistenceException）消息中的 uk_position
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

    /**
     * 沿异常因果链查找指定类型。
     *
     * @param t      要检查的异常或其异常链入口
     * @param target 要匹配的异常类型
     * @return 存在匹配的异常时返回 true
     */
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
     *
     * @param t               异常链起点
     * @param simpleClassName 待匹配的异常简单类名
     * @return 异常链包含指定简单类名时返回 true
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
     * 表示一次可能抛出异常、由事件存储重试逻辑调用的操作。
     */
    @FunctionalInterface
    interface RetryableAction {
        /**
         * 执行一次需要纳入事件存储重试策略的写入。
         *
         * @throws Exception 本次事件写入操作失败
         */
        void run() throws Exception;
    }

    /**
     * 抽象事件存储两次重试之间的等待，便于测试替换。
     */
    @FunctionalInterface
    interface Sleeper {
        /**
         * 在两次写入尝试之间等待给定时长。
         *
         * @param millis 等待毫秒数
         * @throws InterruptedException 等待期间线程被中断
         */
        void sleep(long millis) throws InterruptedException;
    }
}
