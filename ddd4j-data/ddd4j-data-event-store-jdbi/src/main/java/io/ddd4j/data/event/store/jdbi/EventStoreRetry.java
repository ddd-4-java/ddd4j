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

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EventStore append 重试工具：捕获 {@code uk_position} 唯一约束冲突后
 * 按指数退避自动重试整个 append 操作（保持 {@code expectedVersion} 不变）。
 *
 * <p>检测范围（按顺序短路）：
 * <ol>
 *   <li>{@link IllegalStateException}（乐观锁版本冲突）— 立即抛，不重试</li>
 *   <li>嵌套 {@link SQLIntegrityConstraintViolationException}</li>
 *   <li>任意 cause 链中消息含 "uk_position"/"unique constraint"/"unique index"/"duplicate key"/"duplicate entry"
 *       的 {@link Throwable}（覆盖 Hibernate 包装路径）</li>
 *   <li>类名为 "ConstraintViolationException" 的 cause（兼容 Hibernate，避免编译期依赖）</li>
 * </ol>
 *
 * <p>退避策略：基础延迟 10ms，每次翻倍，加 0-10ms 抖动避免雷鸣群。
 * 默认最大 5 次尝试（含首次）；四次退避总时长上限 190ms（10+20+40+80+抖动）。
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
     * 记录事件写入重试和退避信息的日志记录器。
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
     * 用默认尝试次数和基准延时创建重试策略，实际等待由当前线程执行。
     */
    EventStoreRetry() {
        this(DEFAULT_MAX_ATTEMPTS, BASE_DELAY_MILLIS, Thread::sleep);
    }

    /**
     * 创建参数可控的事件追加重试策略。
     *
     * @param maxAttempts     最多执行次数，至少为一次
     * @param baseDelayMillis 首次退避的基准时间，单位为毫秒
     * @param sleeper         执行重试等待的组件
     * @throws IllegalArgumentException 尝试次数小于一次或基准延时为负数
     */
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
     * <p>判断规则（按顺序短路）：
     * <ol>
     *   <li>{@link IllegalStateException}（乐观锁版本冲突）→ false（不可重试）</li>
     *   <li>顶层或任何 cause 为 {@link SQLIntegrityConstraintViolationException} → true</li>
     *   <li>顶层或任何 cause 的 message 含 "uk_position"/"unique constraint"/"unique index"/"duplicate key"/"duplicate entry" → true</li>
     *   <li>类名为 "ConstraintViolationException" 的 cause（兼容 Hibernate，避免编译期依赖） → true</li>
     * </ol>
     *
     * @param t 检查是否可重试的异常
     * @return 异常链包含允许重试的异常时返回 true
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
     * @param t               异常链起点
     * @param simpleClassName 待匹配的异常简单类名
     * @return 异常链包含指定简单类名时返回 true
     */
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

    /**
     * 单次尝试的业务逻辑。
     */
    @FunctionalInterface
    interface RetryableAction<T> {
        /**
         * 执行一次业务逻辑。
         *
         * @return 业务逻辑的返回值
         * @throws Exception 执行过程中出现的异常，交由重试机制判定是否可重试
         */
        T run() throws Exception;
    }

    /**
     * 抽象 {@code Thread.sleep} 以便测试不真正等待。
     */
    @FunctionalInterface
    interface Sleeper {
        /**
         * 休眠指定时长。
         *
         * @param millis 休眠的毫秒数
         * @throws InterruptedException 休眠过程中线程被中断
         */
        void sleep(long millis) throws InterruptedException;
    }
}
