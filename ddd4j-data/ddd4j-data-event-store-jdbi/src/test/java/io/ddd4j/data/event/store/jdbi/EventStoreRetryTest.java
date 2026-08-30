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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EventStoreRetry} 单元测试。
 *
 * <p>覆盖：成功路径 / 单次失败后成功 / 耗尽重试 / 异常识别 / 退避计算。
 * 使用 {@link EventStoreRetry.Sleeper} 桩避免真实睡眠。
 */
class EventStoreRetryTest {

    /** 桩 Sleeper：仅记录调用次数与延迟，不真正睡眠。 */
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

        String result = retry.execute("op", () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(sleeper.calls).isEmpty();
    }

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

        assertThat(result).isEqualTo("ok");
        assertThat(sleeper.calls).hasSize(1);
    }

    @Test
    void 可重试异常_耗尽所有重试后抛出() {
        RecordingSleeper sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(3, 1L, sleeper);

        assertThatThrownBy(() -> retry.execute("op", () -> {
            throw new SQLIntegrityConstraintViolationException("uk_position");
        })).isInstanceOf(SQLIntegrityConstraintViolationException.class);

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

        // 不可重试：只尝试一次，不睡眠
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(sleeper.calls).isEmpty();
    }

    @Test
    void SQLException_含uk_position关键字_判定可重试() {
        assertThat(EventStoreRetry.isRetriable(
                new SQLException("duplicate key for constraint uk_position")))
                .isTrue();
    }

    @Test
    void SQLException_含unique_constraint关键字_判定可重试() {
        assertThat(EventStoreRetry.isRetriable(
                new SQLException("Unique constraint violation detected")))
                .isTrue();
    }

    @Test
    void 任意RuntimeException_非IllegalState_不可重试() {
        assertThat(EventStoreRetry.isRetriable(new RuntimeException("other"))).isFalse();
        assertThat(EventStoreRetry.isRetriable(new NullPointerException())).isFalse();
    }

    @Test
    void SQLException_不含关键字_不可重试() {
        assertThat(EventStoreRetry.isRetriable(new SQLException("connection lost"))).isFalse();
    }

    @Test
    void 嵌套cause中的SQLException_也判定可重试() {
        SQLException sqlCause = new SQLException("uk_position");
        RuntimeException wrapper = new RuntimeException("wrapped", sqlCause);
        assertThat(EventStoreRetry.isRetriable(wrapper)).isTrue();
    }

    @Test
    void 退避延迟_指数增长() {
        assertThat(EventStoreRetry.computeDelay(1)).isBetween(10L, 20L);   // 10 + 0..10
        assertThat(EventStoreRetry.computeDelay(2)).isBetween(20L, 30L);   // 20 + 0..10
        assertThat(EventStoreRetry.computeDelay(3)).isBetween(40L, 50L);   // 40 + 0..10
        assertThat(EventStoreRetry.computeDelay(4)).isBetween(80L, 90L);   // 80 + 0..10
        assertThat(EventStoreRetry.computeDelay(5)).isBetween(160L, 170L); // 160 + 0..10
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
