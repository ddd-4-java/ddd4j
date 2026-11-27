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
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EventStoreRetry} 单元测试。
 *
 * <p>覆盖：成功路径 / 单次失败后成功 / 耗尽重试 / 异常识别（Hibernate/SQL/JPA 三层）/ 退避计算。
 * 使用 {@link EventStoreRetry.Sleeper} 桩避免真实睡眠。
 */
class EventStoreRetryTest {

    /**
     * \u9a8c\u8bc1<code>sleeper.calls</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void 首次成功_不调用Sleeper() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        retry.execute("op", () -> { /* no-op */ });

        assertThat(sleeper.calls).isEmpty();
    }

    /**
     * \u9a8c\u8bc1<code>sleeper.calls</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void 第二次成功_调用一次Sleeper() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        AtomicInteger attempts = new AtomicInteger(0);

        retry.execute("op", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new SQLIntegrityConstraintViolationException("uk_position");
            }
        });

        assertThat(sleeper.calls).hasSize(1);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>sleeper.calls</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\u3002 */
    @Test
    void 可重试异常_耗尽所有重试后抛出() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        // Panache 的 EventStoreRetry 接受 throws Exception 的 lambda，包装后抛出 RuntimeException，
        // 但 cause 保留 SQLException，因此 isRetriable 仍能通过 cause 链识别 uk_position 冲突
        assertThatThrownBy(() -> retry.execute("op", () -> {
            throw new SQLIntegrityConstraintViolationException("uk_position");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(SQLIntegrityConstraintViolationException.class);

        // 3 attempts => 2 sleeps between attempts
        assertThat(sleeper.calls).hasSize(2);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>attempts.get()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>sleeper.calls</code>\u4e3a\u7a7a\u3002 */
    @Test
    void IllegalStateException_乐观锁失败_不重试_立即抛出() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retry.execute("op", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Version conflict");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(sleeper.calls).isEmpty();
    }

    /** \u9a8c\u8bc1<code>EventStoreRetry.isRetriable(wrapper)</code>\u4e3a\u771f\u3002 */
    @Test
    void JPA_PersistenceException_嵌套SQLException_含uk_position_可重试() {
        SQLException sqlCause = new SQLException("uk_position violation");
        PersistenceException wrapper = new PersistenceException("wrapped", sqlCause);

        assertThat(EventStoreRetry.isRetriable(wrapper)).isTrue();
    }

    /** \u9a8c\u8bc1<code>EventStoreRetry.isRetriable(new RuntimeException("other"))</code>\u4e3a\u5047\uff0c<code>EventStoreRetry.isRetriable(new NullPointerException())</code>\u4e3a\u5047\uff0c<code>EventStoreRetry.isRetriable(new IllegalArgumentException("bad input"))</code>\u4e3a\u5047\u3002 */
    @Test
    void 任意RuntimeException_非IllegalState_不可重试() {
        assertThat(EventStoreRetry.isRetriable(new RuntimeException("other"))).isFalse();
        assertThat(EventStoreRetry.isRetriable(new NullPointerException())).isFalse();
        assertThat(EventStoreRetry.isRetriable(new IllegalArgumentException("bad input"))).isFalse();
    }

    /** \u9a8c\u8bc1<code>EventStoreRetry.isRetriable(new SQLException("connection lost"))</code>\u4e3a\u5047\u3002 */
    @Test
    void SQLException_不含关键字_不可重试() {
        assertThat(EventStoreRetry.isRetriable(new SQLException("connection lost"))).isFalse();
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>EventStoreRetry.computeDelay()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void 退避延迟_指数增长() {
        assertThat(EventStoreRetry.computeDelay(1)).isBetween(10L, 20L);
        assertThat(EventStoreRetry.computeDelay(2)).isBetween(20L, 30L);
        assertThat(EventStoreRetry.computeDelay(3)).isBetween(40L, 50L);
        assertThat(EventStoreRetry.computeDelay(4)).isBetween(80L, 90L);
        assertThat(EventStoreRetry.computeDelay(5)).isBetween(160L, 170L);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void maxAttempts_小于1_构造失败() {
        assertThatThrownBy(() -> new EventStoreRetry(0, 1L, ms -> {
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void baseDelayMillis_负数_构造失败() {
        assertThatThrownBy(() -> new EventStoreRetry(3, -1L, ms -> {
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseDelayMillis");
    }

    /** \u6536\u96c6\u6536\u5230\u7684\u7b49\u5f85\u65f6\u957f\uff0c\u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u91cd\u8bd5\u95f4\u9694\u3002 */
    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        /** \u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528\u3002 */
        final List<Long> calls = new ArrayList<>();

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
