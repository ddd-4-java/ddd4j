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

    @Test
    void 任意RuntimeException_message含uk_position关键字_判定可重试() {
        // 模拟 Hibernate 把 SQLException 包装为 PersistenceException 时，
        // cause 链可能不包含 SQLException 但 message 仍带 uk_position 字样。
        RuntimeException wrapped = new RuntimeException(
                "Unique index uk_position violation detected");
        assertThat(EventStoreRetry.isRetriable(wrapped)).isTrue();
    }

    @Test
    void 任意RuntimeException_message含unique_index关键字_判定可重试() {
        // H2 错误信息原文："Unique index uk_position violation"
        RuntimeException wrapped = new RuntimeException("Unique index uk_position violation");
        assertThat(EventStoreRetry.isRetriable(wrapped)).isTrue();
    }

    @Test
    void 任意RuntimeException_message含duplicate_entry关键字_判定可重试() {
        RuntimeException wrapped = new RuntimeException("Duplicate entry 'x' for key 'uk_position'");
        assertThat(EventStoreRetry.isRetriable(wrapped)).isTrue();
    }

    @Test
    void cause链中任意Throwable_message含uk_position_判定可重试() {
        // 深层嵌套场景：顶层 RuntimeException → cause RuntimeException → message 含关键字
        RuntimeException deep = new RuntimeException("uk_position violation");
        RuntimeException outer = new RuntimeException("outer wrapper", deep);
        assertThat(EventStoreRetry.isRetriable(outer)).isTrue();
    }

    @Test
    void cause链中类名ConstraintViolationException_判定可重试() {
        // 模拟 Hibernate ConstraintViolationException：编译期不可见，仅按类名匹配。
        Throwable hibernateCve = createThrowableByName(
                "org.hibernate.exception.ConstraintViolationException",
                "Unique index uk_position violation");
        RuntimeException wrapped = new RuntimeException("PersistenceException wrapper", hibernateCve);
        assertThat(EventStoreRetry.isRetriable(wrapped)).isTrue();
    }

    @Test
    void 线程中断_sleeper被中断_恢复中断标志() throws Exception {
        AtomicInteger interruptedFlagAfter = new AtomicInteger(0);
        EventStoreRetry.Sleeper interruptingSleeper = millis -> {
            throw new InterruptedException("test interrupt");
        };
        EventStoreRetry retry = new EventStoreRetry(3, 1L, interruptingSleeper);

        // 验证：retry 把 InterruptedException 包成 RuntimeException，但保留线程中断标志
        assertThatThrownBy(() -> retry.execute("op", () -> {
            throw new SQLIntegrityConstraintViolationException("uk_position");
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("interrupted");

        // 必须在 catch 之后再次检查中断标志，确保被恢复
        assertThat(Thread.currentThread().isInterrupted())
                .as("Thread interrupt flag must be restored after InterruptedException handling")
                .isTrue();
        // 清理中断标志，避免污染同线程后续测试
        Thread.interrupted();
    }

    /**
     * 通过反射构造指定类名的 Throwable 实例（用于模拟 Hibernate 等编译期不可见的类）。
     * 若类不存在则返回带相同消息的普通 RuntimeException。
     */
    private static Throwable createThrowableByName(String className, String message) {
        try {
            Class<?> cls = Class.forName(className);
            return (Throwable) cls.getConstructor(String.class).newInstance(message);
        } catch (Throwable reflectFailure) {
            // 编译期无 hibernate-core 时回退为带相同类名的 RuntimeException 子类（不会真实触发）
            return new RuntimeException(message) {
                @Override
                public String toString() {
                    return className + ": " + message;
                }
            };
        }
    }
}
