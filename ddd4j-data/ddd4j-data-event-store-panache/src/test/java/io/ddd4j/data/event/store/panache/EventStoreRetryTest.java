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
     * 验证<code>sleeper.calls</code>为空。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void 首次成功_不调用Sleeper() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        retry.execute("op", () -> { /* no-op */ });

        assertThat(sleeper.calls).isEmpty();
    }

    /**
     * 验证<code>sleeper.calls</code> 的大小为 <code>1</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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

    /**
     * 验证被测调用 抛出 <code>RuntimeException.class</code>，<code>sleeper.calls</code> 的大小为 <code>2</code>。
     */
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

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>attempts.get()</code> 等于 <code>1</code>，<code>sleeper.calls</code>为空。
     */
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

    /**
     * 验证<code>EventStoreRetry.isRetriable(wrapper)</code>为真。
     */
    @Test
    void JPA_PersistenceException_嵌套SQLException_含uk_position_可重试() {
        SQLException sqlCause = new SQLException("uk_position violation");
        PersistenceException wrapper = new PersistenceException("wrapped", sqlCause);

        assertThat(EventStoreRetry.isRetriable(wrapper)).isTrue();
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(new RuntimeException("other"))</code>为假，<code>EventStoreRetry.isRetriable(new NullPointerException())</code>为假，<code>EventStoreRetry.isRetriable(new IllegalArgumentException("bad input"))</code>为假。
     */
    @Test
    void 任意RuntimeException_非IllegalState_不可重试() {
        assertThat(EventStoreRetry.isRetriable(new RuntimeException("other"))).isFalse();
        assertThat(EventStoreRetry.isRetriable(new NullPointerException())).isFalse();
        assertThat(EventStoreRetry.isRetriable(new IllegalArgumentException("bad input"))).isFalse();
    }

    /**
     * 验证<code>EventStoreRetry.isRetriable(new SQLException("connection lost"))</code>为假。
     */
    @Test
    void SQLException_不含关键字_不可重试() {
        assertThat(EventStoreRetry.isRetriable(new SQLException("connection lost"))).isFalse();
    }

    /**
     * 在测试框架中执行 <code>EventStoreRetry.computeDelay()</code>，验证该场景。
     */
    @Test
    void 退避延迟_指数增长() {
        assertThat(EventStoreRetry.computeDelay(1)).isBetween(10L, 20L);
        assertThat(EventStoreRetry.computeDelay(2)).isBetween(20L, 30L);
        assertThat(EventStoreRetry.computeDelay(3)).isBetween(40L, 50L);
        assertThat(EventStoreRetry.computeDelay(4)).isBetween(80L, 90L);
        assertThat(EventStoreRetry.computeDelay(5)).isBetween(160L, 170L);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void maxAttempts_小于1_构造失败() {
        assertThatThrownBy(() -> new EventStoreRetry(0, 1L, ms -> {
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void baseDelayMillis_负数_构造失败() {
        assertThatThrownBy(() -> new EventStoreRetry(3, -1L, ms -> {
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseDelayMillis");
    }

    /**
     * 收集收到的等待时长，供测试验证重试间隔。
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
}
