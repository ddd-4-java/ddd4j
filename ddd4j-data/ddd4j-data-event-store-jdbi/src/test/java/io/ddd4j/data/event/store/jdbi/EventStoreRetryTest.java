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

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EventStoreRetry} \u5355\u5143\u6d4b\u8bd5\u3002
 *
 *  <p>\u8986\u76d6\uff1a\u6210\u529f\u8def\u5f84 / \u5355\u6b21\u5931\u8d25\u540e\u6210\u529f / \u8017\u5c3d\u91cd\u8bd5 / \u5f02\u5e38\u8bc6\u522b / \u9000\u907f\u8ba1\u7b97\u3002
 *  \u4f7f\u7528 {@link EventStoreRetry.Sleeper} \u6869\u907f\u514d\u771f\u5b9e\u7761\u7720\u3002
 */
class EventStoreRetryTest {

    /**
     * 验证<code>result</code> 等于 <code>"ok"</code>，<code>sleeper.calls.isEmpty()</code>为真。
     *
     * @throws Exception 完成 <code>first Attempt Success does Not Sleep</code> 操作失败
     */
    @Test
    void firstAttemptSuccess_doesNotSleep() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        String result = retry.execute("op", () -> "ok");

        assertEquals("ok", result);
        assertTrue(sleeper.calls.isEmpty());
    }

    /**
     * 验证<code>result</code> 等于 <code>"ok"</code>，<code>sleeper.calls.size()</code> 等于 <code>1</code>。
     *
     * @throws Exception 完成 <code>retriable Failure Then Success sleeps Once</code> 操作失败
     */
    @Test
    void retriableFailureThenSuccess_sleepsOnce() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        final AtomicInteger attempts = new AtomicInteger(0);

        String result = retry.execute("op", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new SQLIntegrityConstraintViolationException("uk_position violation");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, sleeper.calls.size());
    }

    /** 验证被测调用 抛出 <code>SQLIntegrityConstraintViolationException.class</code>，<code>sleeper.calls.size()</code> 等于 <code>2</code>。 */
    @Test
    void retriableExhausted_throwsLastException() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        assertThrows(SQLIntegrityConstraintViolationException.class, () -> retry.execute("op", () -> {
            throw new SQLIntegrityConstraintViolationException("uk_position");
        }));

        // 3 attempts => 2 sleeps between attempts
        assertEquals(2, sleeper.calls.size());
    }

    /** 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>attempts.get()</code> 等于 <code>1</code>，<code>sleeper.calls.isEmpty()</code>为真。 */
    @Test
    void optimisticLockFailure_noRetry() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        final AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(IllegalStateException.class, () -> retry.execute("op", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Version conflict");
        }));

        // 不可重试：只尝试一次，不睡眠
        assertEquals(1, attempts.get());
        assertTrue(sleeper.calls.isEmpty());
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>SQLException.class</code>\uff0c<code>attempts.get()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>sleeper.calls.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void nonRetriableException_thrownImmediately() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        final AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(SQLException.class, () -> retry.execute("op", () -> {
            attempts.incrementAndGet();
            throw new SQLException("connection closed");
        }));

        assertEquals(1, attempts.get());
        assertTrue(sleeper.calls.isEmpty());
    }

    /** 验证<code>delay &gt;= expDelay &amp;&amp; delay &lt;= expDelay + 10</code>为真。 */
    @Test
    void computeDelay_isExponentialWithJitter() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            long delay = EventStoreRetry.computeDelay(attempt);
            long expDelay = 10L << (attempt - 1);
            // jitter ∈ [0, 10]
            assertTrue(delay >= expDelay && delay <= expDelay + 10,
                    "attempt " + attempt + " delay " + delay + " not in [" + expDelay + "," + (expDelay + 10) + "]");
        }
    }

    /** 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。 */
    @Test
    void invalidConstructorArgs_throwIAE() {
        RecordingSleeper sleeper = new RecordingSleeper();
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new EventStoreRetry(0, 1L, sleeper)).getMessage().contains("maxAttempts"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new EventStoreRetry(3, -1L, sleeper)).getMessage().contains("baseDelayMillis"));
    }

    /** 验证<code>EventStoreRetry.isRetriable(new SQLException("duplicate key for constraint uk_position"))</code>为真，<code>EventStoreRetry.isRetriable(new SQLException("Unique constraint violation detected"))</code>为真，<code>EventStoreRetry.isRetriable(new RuntimeException("Unique index uk_position violation"))</code>为真。 */
    @Test
    void sqlAndWrappedConstraintMessages_areRetriable() {
        assertTrue(EventStoreRetry.isRetriable(new SQLException("duplicate key for constraint uk_position")));
        assertTrue(EventStoreRetry.isRetriable(new SQLException("Unique constraint violation detected")));
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("Unique index uk_position violation")));
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("Duplicate entry 'x' for key 'uk_position'")));
        assertTrue(EventStoreRetry.isRetriable(new RuntimeException("outer wrapper",
                new RuntimeException("middle wrapper", new SQLException("uk_position")))));
    }

    /** 验证<code>EventStoreRetry.isRetriable(new SQLException("connection lost"))</code>为假，<code>EventStoreRetry.isRetriable(new RuntimeException("other"))</code>为假，<code>EventStoreRetry.isRetriable(new NullPointerException())</code>为假。 */
    @Test
    void unrelatedExceptions_areNotRetriable() {
        assertFalse(EventStoreRetry.isRetriable(new SQLException("connection lost")));
        assertFalse(EventStoreRetry.isRetriable(new RuntimeException("other")));
        assertFalse(EventStoreRetry.isRetriable(new NullPointerException()));
    }

    /** 验证<code>EventStoreRetry.isRetriable(wrapper)</code>为真。 */
    @Test
    void constraintViolationClassName_isRetriableWithoutCompileTimeDependency() {
        RuntimeException wrapper = new RuntimeException("PersistenceException wrapper",
                new ConstraintViolationException("schema constraint rejected"));
        assertTrue(EventStoreRetry.isRetriable(wrapper));
    }

    /** 验证被测调用 抛出 <code>RuntimeException.class</code>，<code>failure.getMessage().contains("interrupted")</code>为真，<code>Thread.currentThread().isInterrupted()</code>为真。 */
    @Test
    void interruptedSleep_restoresInterruptFlag() {
        EventStoreRetry retry = new EventStoreRetry(3, 1L, millis -> {
            throw new InterruptedException("test interrupt");
        });
        try {
            RuntimeException failure = assertThrows(RuntimeException.class, () -> retry.execute("op", () -> {
                throw new SQLIntegrityConstraintViolationException("uk_position");
            }));
            assertTrue(failure.getMessage().contains("interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /** \u4ec5\u7528\u7b80\u5355\u7c7b\u540d\u89e6\u53d1\u517c\u5bb9\u8def\u5f84\uff1b\u6d88\u606f\u4e0d\u542b\u4efb\u4f55\u53ef\u91cd\u8bd5\u5173\u952e\u5b57\u3002 */
    private static final class ConstraintViolationException extends RuntimeException {
        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>ConstraintViolationException</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param message \u6d4b\u8bd5\u4f20\u9012\u6216\u7528\u4e8e\u8bca\u65ad\u7684\u6d88\u606f
         */
        private ConstraintViolationException(String message) {
            super(message);
        }
    }

    /**
     * 桩 Sleeper：仅记录调用次数与延迟，不真正睡眠。
     */
    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        /** \u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528\u3002 */
        final List<Long> calls = new ArrayList<Long>();

        /**
         * \u8c03\u7528 <code>calls.add()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param millis \u7b49\u5f85\u6216\u63a8\u8fdb\u7684\u6beb\u79d2\u6570
         */
        @Override
        public void sleep(long millis) {
            calls.add(millis);
        }
    }
}
