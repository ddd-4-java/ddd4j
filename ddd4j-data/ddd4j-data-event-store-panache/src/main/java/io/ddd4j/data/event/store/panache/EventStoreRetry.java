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
 * Panache EventStore \u91cd\u8bd5\u5de5\u5177\uff1a\u6355\u83b7 {@code uk_position} \u552f\u4e00\u7ea6\u675f\u51b2\u7a81\u540e
 *  \u6309\u6307\u6570\u9000\u907f\u81ea\u52a8\u91cd\u8bd5\u6574\u4e2a append \u64cd\u4f5c\uff08\u4fdd\u6301 {@code expectedVersion} \u4e0d\u53d8\uff09\u3002
 *
 *  <p>\u68c0\u6d4b\u8303\u56f4\uff08\u6309\u987a\u5e8f\u77ed\u8def\uff09\uff1a
 *  <ol>
 *    <li>{@link IllegalStateException}\uff08\u4e50\u89c2\u9501\u7248\u672c\u51b2\u7a81\uff09\u2014 \u7acb\u5373\u629b\uff0c\u4e0d\u91cd\u8bd5</li>
 *    <li>{@link PersistenceException} \u5d4c\u5957 {@link org.hibernate.exception.ConstraintViolationException} \u6216 {@link SQLException}</li>
 *    <li>\u4efb\u4f55 cause \u94fe\u4e2d\u7684 {@link SQLIntegrityConstraintViolationException}</li>
 *    <li>\u6d88\u606f\u542b "uk_position"/"unique constraint"/"duplicate key"/"duplicate entry" \u7684 SQLException</li>
 *  </ol>
 *
 *  <p>\u9000\u907f\uff1a\u57fa\u7840 10ms\uff0c\u6307\u6570\u7ffb\u500d + 0-10ms \u6296\u52a8\u3002\u9ed8\u8ba4\u6700\u591a 5 \u6b21\u5c1d\u8bd5\uff0c\u603b\u8017\u65f6 ~310ms \u4e0a\u9650\u3002
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
final class EventStoreRetry {

    /** \u91cd\u8bd5\u7b56\u7565\u5141\u8bb8\u6267\u884c\u64cd\u4f5c\u7684\u9ed8\u8ba4\u6700\u5927\u6b21\u6570\u3002 */
    static final int DEFAULT_MAX_ATTEMPTS = 5;
    /** \u6307\u6570\u9000\u907f\u53ca\u968f\u673a\u6296\u52a8\u4f7f\u7528\u7684\u57fa\u51c6\u6beb\u79d2\u6570\u3002 */
    static final long BASE_DELAY_MILLIS = 10L;
    /** \u7528\u4e8e\u8bb0\u5f55\u4e8b\u4ef6\u5b58\u50a8\u91cd\u8bd5\u548c\u5931\u8d25\u7684\u65e5\u5fd7\u8bb0\u5f55\u5668\u3002 */
    private static final Logger LOG = LoggerFactory.getLogger(EventStoreRetry.class);
    /** \u5f53\u524d\u7b56\u7565\u5141\u8bb8\u6267\u884c\u4e00\u6b21\u4e8b\u4ef6\u5199\u5165\u7684\u6700\u591a\u5c1d\u8bd5\u6b21\u6570\u3002 */
    private final int maxAttempts;
    /** \u91cd\u8bd5\u76f8\u90bb\u5c1d\u8bd5\u4e4b\u95f4\u7684\u57fa\u7840\u5ef6\u65f6\uff0c\u5355\u4f4d\u4e3a\u6beb\u79d2\u3002 */
    private final long baseDelayMillis;
    /** \u5728\u4e24\u6b21\u91cd\u8bd5\u4e4b\u95f4\u6267\u884c\u7b49\u5f85\u7684\u51fd\u6570\u3002 */
    private final Sleeper sleeper;

    /** \u4ee5\u9ed8\u8ba4\u5c1d\u8bd5\u6b21\u6570\u3001\u9000\u907f\u914d\u7f6e\u548c\u7ebf\u7a0b\u7b49\u5f85\u884c\u4e3a\u88c5\u914d\u91cd\u8bd5\u7b56\u7565\u3002 */
    EventStoreRetry() {
        this(DEFAULT_MAX_ATTEMPTS, BASE_DELAY_MILLIS, Thread::sleep);
    }

    /**
     * \u6309\u7ed9\u5b9a\u6b21\u6570\u548c\u7b49\u5f85\u7b56\u7565\u521b\u5efa\u4e8b\u4ef6\u8ffd\u52a0\u91cd\u8bd5\u903b\u8f91\u3002
     * @param maxAttempts \u64cd\u4f5c\u6700\u591a\u5141\u8bb8\u5c1d\u8bd5\u7684\u6b21\u6570
     * @param baseDelayMillis \u8ba1\u7b97\u9000\u907f\u7684\u57fa\u7840\u6beb\u79d2\u6570
     * @param sleeper \u6267\u884c\u91cd\u8bd5\u7b49\u5f85\u7684\u7ec4\u4ef6
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
     * \u62c6\u5305 checked \u5f02\u5e38\uff1a\u5982\u679c\u5f02\u5e38\u672c\u8eab\u662f RuntimeException \u6216 Error \u76f4\u63a5\u8fd4\u56de\uff1b\u5426\u5219\u5305\u88c5\u3002
     *  \u5305\u88c5\u65f6\u4f18\u5148\u4fdd\u7559 cause\uff08\u7528\u4e8e isRetriable \u904d\u5386 cause \u94fe\uff09\u3002
     *
     * @param raw \u5e95\u5c42\u64cd\u4f5c\u629b\u51fa\u7684\u539f\u59cb\u5f02\u5e38
     * @return \u89e3\u5305\u540e\u7684\u8fd0\u884c\u65f6\u5f02\u5e38\uff0c\u5fc5\u8981\u65f6\u5305\u88c5\u539f\u5f02\u5e38
     */
    private static RuntimeException unwrapToRuntime(Exception raw) {
        if (raw instanceof RuntimeException) {
            return (RuntimeException) raw;
        }
        return new RuntimeException(raw);
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
     * @param t \u68c0\u67e5\u662f\u5426\u53ef\u91cd\u8bd5\u7684\u5f02\u5e38
     * @return \u5f02\u5e38\u94fe\u5305\u542b\u5141\u8bb8\u91cd\u8bd5\u7684\u5f02\u5e38\u65f6\u8fd4\u56de true
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
     *  <p>\u7528\u4e8e\u68c0\u6d4b {@code org.hibernate.exception.ConstraintViolationException}\u2014\u2014\u5b83\u5728
     *  Hibernate \u552f\u4e00\u7ea6\u675f\u51b2\u7a81\u65f6\u7531 JPA {@code PersistenceException} \u5305\u88c5\uff0c\u4f46\u6a21\u5757\u7684
     *  compile classpath \u4e0d\u542b hibernate-core\uff08\u5728 test scope\uff09\u3002\u8fd0\u884c\u65f6\u7684 hibernate-core
     *  \u6765\u81ea {@code quarkus-hibernate-orm-panache} \u4f20\u9012\u4f9d\u8d56\u3002
     *
     * @param t \u5f02\u5e38\u94fe\u8d77\u70b9
     * @param simpleClassName \u5f85\u5339\u914d\u7684\u5f02\u5e38\u7b80\u5355\u7c7b\u540d
     * @return \u5f02\u5e38\u94fe\u5305\u542b\u6307\u5b9a\u7b80\u5355\u7c7b\u540d\u65f6\u8fd4\u56de true
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

    /** \u8868\u793a\u4e00\u6b21\u53ef\u80fd\u629b\u51fa\u5f02\u5e38\u3001\u7531\u4e8b\u4ef6\u5b58\u50a8\u91cd\u8bd5\u903b\u8f91\u8c03\u7528\u7684\u64cd\u4f5c\u3002 */
    @FunctionalInterface
    interface RetryableAction {
        /**
         * \u6267\u884c\u4e00\u6b21\u9700\u8981\u7eb3\u5165\u4e8b\u4ef6\u5b58\u50a8\u91cd\u8bd5\u7b56\u7565\u7684\u5199\u5165\u3002
         * @throws Exception \u672c\u6b21\u4e8b\u4ef6\u5199\u5165\u64cd\u4f5c\u5931\u8d25
         */
        void run() throws Exception;
    }

    /** \u62bd\u8c61\u4e8b\u4ef6\u5b58\u50a8\u4e24\u6b21\u91cd\u8bd5\u4e4b\u95f4\u7684\u7b49\u5f85\uff0c\u4fbf\u4e8e\u6d4b\u8bd5\u66ff\u6362\u3002 */
    @FunctionalInterface
    interface Sleeper {
        /**
         * \u5728\u4e24\u6b21\u5199\u5165\u5c1d\u8bd5\u4e4b\u95f4\u7b49\u5f85\u7ed9\u5b9a\u65f6\u957f\u3002
         * @param millis \u7b49\u5f85\u6beb\u79d2\u6570
         * @throws InterruptedException \u7b49\u5f85\u671f\u95f4\u7ebf\u7a0b\u88ab\u4e2d\u65ad
         */
        void sleep(long millis) throws InterruptedException;
    }
}
