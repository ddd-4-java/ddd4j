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

    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        final List<Long> calls = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            calls.add(millis);
        }
    }

    @Test
    void 首次成功_不调用Sleeper() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        retry.execute("op", () -> { /* no-op */ });

        assertThat(sleeper.calls).isEmpty();
    }

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

    @Test
    void JPA_PersistenceException_嵌套SQLException_含uk_position_可重试() {
        SQLException sqlCause = new SQLException("uk_position violation");
        PersistenceException wrapper = new PersistenceException("wrapped", sqlCause);

        assertThat(EventStoreRetry.isRetriable(wrapper)).isTrue();
    }

    @Test
    void 任意RuntimeException_非IllegalState_不可重试() {
        assertThat(EventStoreRetry.isRetriable(new RuntimeException("other"))).isFalse();
        assertThat(EventStoreRetry.isRetriable(new NullPointerException())).isFalse();
        assertThat(EventStoreRetry.isRetriable(new IllegalArgumentException("bad input"))).isFalse();
    }

    @Test
    void SQLException_不含关键字_不可重试() {
        assertThat(EventStoreRetry.isRetriable(new SQLException("connection lost"))).isFalse();
    }

    @Test
    void 退避延迟_指数增长() {
        assertThat(EventStoreRetry.computeDelay(1)).isBetween(10L, 20L);
        assertThat(EventStoreRetry.computeDelay(2)).isBetween(20L, 30L);
        assertThat(EventStoreRetry.computeDelay(3)).isBetween(40L, 50L);
        assertThat(EventStoreRetry.computeDelay(4)).isBetween(80L, 90L);
        assertThat(EventStoreRetry.computeDelay(5)).isBetween(160L, 170L);
    }

    @Test
    void maxAttempts_小于1_构造失败() {
        assertThatThrownBy(() -> new EventStoreRetry(0, 1L, ms -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    @Test
    void baseDelayMillis_负数_构造失败() {
        assertThatThrownBy(() -> new EventStoreRetry(3, -1L, ms -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseDelayMillis");
    }
}
