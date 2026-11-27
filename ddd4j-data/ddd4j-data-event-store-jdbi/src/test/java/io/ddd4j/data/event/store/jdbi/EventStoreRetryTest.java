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
 * {@link EventStoreRetry} 单元测试。
 *
 * <p>覆盖：成功路径 / 单次失败后成功 / 耗尽重试 / 异常识别 / 退避计算。
 * 使用 {@link EventStoreRetry.Sleeper} 桩避免真实睡眠。
 */
class EventStoreRetryTest {

    /**
     * 验证<code>result</code> 等于 <code>"ok"</code>，<code>sleeper.calls.isEmpty()</code>为真。
     *
     * @throws Exception 执行被测拦截器或调用失败
     */
    @Test
    void 首次成功_不调用Sleeper() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        String result = retry.execute("op", () -> "ok");

        assertEquals("ok", result);
        assertTrue(sleeper.calls.isEmpty());
    }

    /**
     * 验证<code>result</code> 等于 <code>"ok"</code>，<code>sleeper.calls.size()</code> 等于 <code>1</code>。
     *
     * @throws Exception 执行被测拦截器或调用失败
     */
    @Test
    void 第二次成功_调用一次Sleeper() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = retry.execute("op", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new SQLIntegrityConstraintViolationException("uk_position violation");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, sleeper.calls.size());
    }

    /**
     * 验证被测调用 抛出 <code>SQLIntegrityConstraintViolationException.class</code>，<code>sleeper.calls.size()</code> 等于 <code>2</code>。
     */
    @Test
    void 可重试异常_耗尽所有重试后抛出() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        assertThrows(SQLIntegrityConstraintViolationException.class, () -> retry.execute("op", () -> {
            throw new SQLIntegrityConstraintViolationException("uk_position");
        }));

        // 3 attempts => 2 sleeps between attempts
        assertEquals(2, sleeper.calls.size());
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>attempts.get()</code> 等于 <code>1</code>，<code>sleeper.calls.isEmpty()</code>为真。
     */
    @Test
    void IllegalStateException_乐观锁失败_不重试_立即抛出() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(IllegalStateException.class, () -> retry.execute("op", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Version conflict");
        }));

        // 不可重试：只尝试一次，不睡眠
        assertEquals(1, attempts.get());
        assertTrue(sleeper.calls.isEmpty());
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable( new SQLException("duplicate key for constraint uk_position"))</code>为真。
     */
    @Test
    void SQLException_含uk_position关键字_判定可重试() {
        assertTrue(EventStoreRetry.isRetriable(
                new SQLException("duplicate key for constraint uk_position")));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable( new SQLException("Unique constraint violation detected"))</code>为真。
     */
    @Test
    void SQLException_含unique_constraint关键字_判定可重试() {
        assertTrue(EventStoreRetry.isRetriable(
                new SQLException("Unique constraint violation detected")));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(new RuntimeException("other"))</code>为假，<code>EventStoreRetry.isRetriable(new NullPointerException())</code>为假。
     */
    @Test
    void 任意RuntimeException_非IllegalState_不可重试() {
        assertFalse(EventStoreRetry.isRetriable(new RuntimeException("other")));
        assertFalse(EventStoreRetry.isRetriable(new NullPointerException()));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(new SQLException("connection lost"))</code>为假。
     */
    @Test
    void SQLException_不含关键字_不可重试() {
        assertFalse(EventStoreRetry.isRetriable(new SQLException("connection lost")));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(wrapper)</code>为真，<code>EventStoreRetry.isRetriable(outer)</code>为真。
     */
    @Test
    void 嵌套cause中的SQLException_也判定可重试() {
        SQLException sqlCause = new SQLException("uk_position");
        RuntimeException wrapper = new RuntimeException("wrapped", sqlCause);
        assertTrue(EventStoreRetry.isRetriable(wrapper));
        RuntimeException outer = new RuntimeException("outer wrapper", wrapper);
        assertTrue(EventStoreRetry.isRetriable(outer));
    }

    /**
     * 验证<code>delay &gt;= minimum &amp;&amp; delay &lt;= minimum + 10L</code>为真。
     */
    @Test
    void 退避延迟_指数增长() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            long delay = EventStoreRetry.computeDelay(attempt);
            long minimum = 10L << (attempt - 1);
            assertTrue(delay >= minimum && delay <= minimum + 10L);
        }
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>，<code>error.getMessage().contains("maxAttempts")</code>为真。
     */
    @Test
    void maxAttempts_小于1_构造失败() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new EventStoreRetry(0, 1L, ms -> {
                }));
        assertTrue(error.getMessage().contains("maxAttempts"));
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>，<code>error.getMessage().contains("baseDelayMillis")</code>为真。
     */
    @Test
    void baseDelayMillis_负数_构造失败() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new EventStoreRetry(3, -1L, ms -> {
                }));
        assertTrue(error.getMessage().contains("baseDelayMillis"));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(wrapped)</code>为真。
     */
    @Test
    void 任意RuntimeException_message含uk_position关键字_判定可重试() {
        // 模拟 Hibernate 把 SQLException 包装为 PersistenceException 时，
        // cause 链可能不包含 SQLException 但 message 仍带 uk_position 字样。
        RuntimeException wrapped = new RuntimeException(
                "Unique index uk_position violation detected");
        assertTrue(EventStoreRetry.isRetriable(wrapped));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(wrapped)</code>为真。
     */
    @Test
    void 任意RuntimeException_message含unique_index关键字_判定可重试() {
        // H2 错误信息原文："Unique index uk_position violation"
        RuntimeException wrapped = new RuntimeException("Unique index uk_position violation");
        assertTrue(EventStoreRetry.isRetriable(wrapped));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(wrapped)</code>为真。
     */
    @Test
    void 任意RuntimeException_message含duplicate_entry关键字_判定可重试() {
        RuntimeException wrapped = new RuntimeException("Duplicate entry 'x' for key 'uk_position'");
        assertTrue(EventStoreRetry.isRetriable(wrapped));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(outer)</code>为真。
     */
    @Test
    void cause链中任意Throwable_message含uk_position_判定可重试() {
        // 深层嵌套场景：顶层 RuntimeException → cause RuntimeException → message 含关键字
        RuntimeException deep = new RuntimeException("uk_position violation");
        RuntimeException outer = new RuntimeException("outer wrapper", deep);
        assertTrue(EventStoreRetry.isRetriable(outer));
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(wrapped)</code>为真。
     */
    @Test
    void cause链中类名ConstraintViolationException_判定可重试() {
        // 模拟 Hibernate ConstraintViolationException：编译期不可见，仅按类名匹配。
        Throwable hibernateCve = new ConstraintViolationException("schema constraint rejected");
        RuntimeException wrapped = new RuntimeException("PersistenceException wrapper", hibernateCve);
        assertTrue(EventStoreRetry.isRetriable(wrapped));
    }

    /**
     * 验证被测调用 抛出 <code>RuntimeException.class</code>，<code>error.getMessage().contains("interrupted")</code>为真，<code>Thread.currentThread().isInterrupted()</code>为真。
     *
     * @throws Exception 执行被测拦截器或调用失败
     */
    @Test
    void 线程中断_sleeper被中断_恢复中断标志() throws Exception {
        EventStoreRetry.Sleeper interruptingSleeper = millis -> {
            throw new InterruptedException("test interrupt");
        };
        EventStoreRetry retry = new EventStoreRetry(3, 1L, interruptingSleeper);

        // 验证：retry 把 InterruptedException 包成 RuntimeException，但保留线程中断标志
        try {
            RuntimeException error = assertThrows(RuntimeException.class, () -> retry.execute("op", () -> {
                throw new SQLIntegrityConstraintViolationException("uk_position");
            }));
            assertTrue(error.getMessage().contains("interrupted"));
            assertTrue(Thread.currentThread().isInterrupted(),
                    "Thread interrupt flag must be restored after InterruptedException handling");
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * 验证被测调用 抛出 <code>SQLException.class</code>，<code>attempts.get()</code> 等于 <code>1</code>，<code>sleeper.calls.isEmpty()</code>为真。
     */
    @Test
    void nonRetriableException_thrownImmediately() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(SQLException.class, () -> retry.execute("op", () -> {
            attempts.incrementAndGet();
            throw new SQLException("connection closed");
        }));

        assertEquals(1, attempts.get());
        assertTrue(sleeper.calls.isEmpty());
    }

    /**
     * 桩 Sleeper：仅记录调用次数与延迟，不真正睡眠。
     */
    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        /**
         * 记录被测入口实际触发的调用。
         */
        final List<Long> calls = new ArrayList<>();

        /**
         * 调用 <code>calls.add()</code> 执行当前夹具操作。
         *
         * @param millis 等待或推进的毫秒数
         */
        @Override
        public void sleep(long millis) {
            calls.add(millis);
        }
    }

    /**
     * 仅用简单类名触发兼容路径；消息不含任何可重试关键字。
     */
    private static final class ConstraintViolationException extends RuntimeException {
        /**
         * 使用 <code>super()</code> 初始化 <code>ConstraintViolationException</code> 测试夹具。
         *
         * @param message 测试传递或用于诊断的消息
         */
        private ConstraintViolationException(String message) {
            super(message);
        }
    }
}
