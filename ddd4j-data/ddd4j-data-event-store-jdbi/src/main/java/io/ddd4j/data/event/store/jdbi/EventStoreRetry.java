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

    /** \u8bb0\u5f55\u4e8b\u4ef6\u5199\u5165\u91cd\u8bd5\u548c\u9000\u907f\u4fe1\u606f\u7684\u65e5\u5fd7\u8bb0\u5f55\u5668\u3002 */
    private static final Logger LOG = LoggerFactory.getLogger(EventStoreRetry.class);

    /** \u91cd\u8bd5\u7b56\u7565\u5141\u8bb8\u6267\u884c\u64cd\u4f5c\u7684\u9ed8\u8ba4\u6700\u5927\u6b21\u6570\u3002 */
    static final int DEFAULT_MAX_ATTEMPTS = 5;
    /** \u6307\u6570\u9000\u907f\u53ca\u968f\u673a\u6296\u52a8\u4f7f\u7528\u7684\u57fa\u51c6\u6beb\u79d2\u6570\u3002 */
    static final long BASE_DELAY_MILLIS = 10L;

    /** \u5f53\u524d\u7b56\u7565\u5141\u8bb8\u6267\u884c\u4e00\u6b21\u4e8b\u4ef6\u5199\u5165\u7684\u6700\u591a\u5c1d\u8bd5\u6b21\u6570\u3002 */
    private final int maxAttempts;
    /** \u91cd\u8bd5\u76f8\u90bb\u5c1d\u8bd5\u4e4b\u95f4\u7684\u57fa\u7840\u5ef6\u65f6\uff0c\u5355\u4f4d\u4e3a\u6beb\u79d2\u3002 */
    private final long baseDelayMillis;
    /** \u5728\u4e24\u6b21\u91cd\u8bd5\u4e4b\u95f4\u6267\u884c\u7b49\u5f85\u7684\u51fd\u6570\u3002 */
    private final Sleeper sleeper;

    /** \u7528\u9ed8\u8ba4\u5c1d\u8bd5\u6b21\u6570\u548c\u57fa\u51c6\u5ef6\u65f6\u521b\u5efa\u91cd\u8bd5\u7b56\u7565\uff0c\u5b9e\u9645\u7b49\u5f85\u7531\u5f53\u524d\u7ebf\u7a0b\u6267\u884c\u3002 */
    EventStoreRetry() {
        this(DEFAULT_MAX_ATTEMPTS, BASE_DELAY_MILLIS, Thread::sleep);
    }

    /**
     * \u521b\u5efa\u53c2\u6570\u53ef\u63a7\u7684\u4e8b\u4ef6\u8ffd\u52a0\u91cd\u8bd5\u7b56\u7565\u3002
     * @param maxAttempts \u6700\u591a\u6267\u884c\u6b21\u6570\uff0c\u81f3\u5c11\u4e3a\u4e00\u6b21
     * @param baseDelayMillis \u9996\u6b21\u9000\u907f\u7684\u57fa\u51c6\u65f6\u95f4\uff0c\u5355\u4f4d\u4e3a\u6beb\u79d2
     * @param sleeper \u6267\u884c\u91cd\u8bd5\u7b49\u5f85\u7684\u7ec4\u4ef6
     * @throws IllegalArgumentException \u5c1d\u8bd5\u6b21\u6570\u5c0f\u4e8e\u4e00\u6b21\u6216\u57fa\u51c6\u5ef6\u65f6\u4e3a\u8d1f\u6570
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
     * \u5c06\u6309\u5c1d\u8bd5\u6b21\u6570\u589e\u957f\u7684\u6307\u6570\u9000\u907f\u4e0e\u968f\u673a\u6296\u52a8\u76f8\u52a0\u3002
     * @param attempt \u5f53\u524d\u6267\u884c\u5931\u8d25\u540e\u7528\u4e8e\u8ba1\u7b97\u9000\u907f\u7684\u5c1d\u8bd5\u7f16\u53f7\uff0c\u4ece 1 \u5f00\u59cb
     * @return \u4e0b\u6b21\u5c1d\u8bd5\u524d\u7b49\u5f85\u7684\u6beb\u79d2\u6570
     */
    static long computeDelay(int attempt) {
        long expDelay = BASE_DELAY_MILLIS << (attempt - 1);
        long jitter = ThreadLocalRandom.current().nextLong(BASE_DELAY_MILLIS + 1);
        return expDelay + jitter;
    }

    /**
     * \u5224\u5b9a\u5f02\u5e38\u662f\u5426\u4e3a\u53ef\u91cd\u8bd5\u7684 {@code uk_position} \u51b2\u7a81\u3002
     *
     *  <p>\u5224\u65ad\u89c4\u5219\uff08\u6309\u987a\u5e8f\u77ed\u8def\uff09\uff1a
     *  <ol>
     *    <li>{@link IllegalStateException}\uff08\u4e50\u89c2\u9501\u7248\u672c\u51b2\u7a81\uff09\u2192 false\uff08\u4e0d\u53ef\u91cd\u8bd5\uff09</li>
     *    <li>\u9876\u5c42\u6216\u4efb\u4f55 cause \u4e3a {@link SQLIntegrityConstraintViolationException} \u2192 true</li>
     *    <li>\u9876\u5c42\u6216\u4efb\u4f55 cause \u7684 message \u542b "uk_position"/"unique constraint"/"unique index"/"duplicate key"/"duplicate entry" \u2192 true</li>
     *    <li>\u7c7b\u540d\u4e3a "ConstraintViolationException" \u7684 cause\uff08\u517c\u5bb9 Hibernate\uff0c\u907f\u514d\u7f16\u8bd1\u671f\u4f9d\u8d56\uff09 \u2192 true</li>
     *  </ol>
     *
     * @param t \u68c0\u67e5\u662f\u5426\u53ef\u91cd\u8bd5\u7684\u5f02\u5e38
     * @return \u5f02\u5e38\u94fe\u5305\u542b\u5141\u8bb8\u91cd\u8bd5\u7684\u5f02\u5e38\u65f6\u8fd4\u56de true
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
     * \u6cbf\u5f02\u5e38\u56e0\u679c\u94fe\u67e5\u627e\u6307\u5b9a\u7c7b\u578b\u3002
     * @param t \u8981\u68c0\u67e5\u7684\u5f02\u5e38\u6216\u5176\u5f02\u5e38\u94fe\u5165\u53e3
     * @param target \u8981\u5339\u914d\u7684\u5f02\u5e38\u7c7b\u578b
     * @return \u5b58\u5728\u5339\u914d\u7684\u5f02\u5e38\u65f6\u8fd4\u56de true
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
     * \u901a\u8fc7\u7c7b\u540d\u5339\u914d\u8bc6\u522b cause \u94fe\u4e2d\u7684\u7279\u5b9a\u5f02\u5e38\uff08\u907f\u514d\u786c\u4f9d\u8d56 Hibernate\uff09\u3002
     *
     * @param t \u5f02\u5e38\u94fe\u8d77\u70b9
     * @param simpleClassName \u5f85\u5339\u914d\u7684\u5f02\u5e38\u7b80\u5355\u7c7b\u540d
     * @return \u5f02\u5e38\u94fe\u5305\u542b\u6307\u5b9a\u7b80\u5355\u7c7b\u540d\u65f6\u8fd4\u56de true
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
