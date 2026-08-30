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

import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JdbiEventStore} append 重试集成测试。
 *
 * <p>覆盖四类端到端场景：
 * <ol>
 *   <li><b>成功路径</b>：append 不触发 Sleeper，事件正确落库</li>
 *   <li><b>乐观锁失败</b>：expectedVersion 不匹配抛 IllegalStateException，<b>不</b>触发 Sleeper，事务回滚</li>
 *   <li><b>真实 uk_position 冲突 + 重试恢复</b>：使用 jdbi {@link SqlStatements} 拦截器，
 *       在第一次 append 的 INSERT 语句上抛 {@link SQLIntegrityConstraintViolationException}，
 *       第二次让真实 SQL 通过——验证 EventStoreRetry 自动捕获并重试成功</li>
 *   <li><b>空事件列表</b>：早期返回，不触发 Sleeper</li>
 * </ol>
 *
 * <p>真实冲突无法通过 H2 内存数据库双连接直接触发（H2 MEM 隔离级别不暴露 uncommitted row），
 * 因此采用 jdbi StatementContext 拦截在应用层制造 {@code uk_position} 冲突，
 * 但消费的是 EventStoreRetry 的真实识别与重试路径（与生产场景的 SQLException 处理等价）。
 */
class JdbiEventStoreRetryTest {

    private Jdbi jdbi;
    private EventStore eventStore;
    private RecordingSleeper sleeper;

    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public void sleep(long millis) {
            calls.incrementAndGet();
        }
    }

    @BeforeEach
    void setUp() {
        String dbName = "eventstore_retry_" + System.nanoTime();
        jdbi = Jdbi.create("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        sleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(5, 1L, sleeper);
        eventStore = new JdbiEventStore(jdbi, retry);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbi.useHandle(handle ->
                    handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
        } catch (Exception ignored) {
            // ignore
        }
    }

    @Test
    void append_成功路径_不触发Sleeper() {
        eventStore.append("agg-1", List.of(new TestEvent("e1", 1L)), 0L);

        assertThat(sleeper.calls.get()).isZero();
        assertThat(eventStore.read("agg-1")).hasSize(1);
    }

    @Test
    void append_乐观锁失败_IllegalStateException_不触发Sleeper_事务回滚() {
        eventStore.append("agg-1", List.of(new TestEvent("e1", 1L)), 0L);

        assertThatThrownBy(() ->
                eventStore.append("agg-1", List.of(new TestEvent("e2", 2L)), 99L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(sleeper.calls.get()).isZero();
        assertThat(eventStore.read("agg-1")).hasSize(1);
    }

    @Test
    void append_uk_position冲突_触发自动重试_最终成功() {
        // 使用 Jdbi 的 SqlLogger 拦截器：第一次 INSERT 调用抛 SQLIntegrityConstraintViolationException
        // （unchecked 包装，避免 SqlLogger 接口的 throws SQLException 约束）；
        // EventStoreRetry.isRetriable 通过类名/类型匹配识别。
        // 第二次正常透传（真实 SQL 执行）——模拟生产中"两个事务并发 INSERT 相同 position"
        // 导致 uk_position 冲突后第一个事务回滚释放 row，第二个事务重试成功的场景。
        String dbName = "eventstore_retry_" + System.nanoTime();
        Jdbi faultJDBI = Jdbi.create("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        AtomicInteger insertCount = new AtomicInteger(0);
        org.jdbi.v3.core.statement.SqlLogger faultLogger = new org.jdbi.v3.core.statement.SqlLogger() {
            @Override
            public void logBeforeExecution(StatementContext context) {
                String sql = context.getRenderedSql().toUpperCase();
                if (sql.startsWith("INSERT") && insertCount.incrementAndGet() == 1) {
                    throw new RuntimeException(
                            new SQLIntegrityConstraintViolationException(
                                    "Unique index uk_position violation"));
                }
            }
        };
        faultJDBI.getConfig(SqlStatements.class).setSqlLogger(faultLogger);

        RecordingSleeper retrySleeper = new RecordingSleeper();
        EventStoreRetry retry = new EventStoreRetry(5, 1L, retrySleeper);
        EventStore faultStore = new JdbiEventStore(faultJDBI, retry);

        // 应成功：第一次 INSERT 触发 uk_position 冲突，重试后第二次 INSERT 成功
        faultStore.append("agg-1", List.of(new TestEvent("e1", 1L)), 0L);

        // 验证重试被触发（至少一次 Sleeper 调用）
        assertThat(retrySleeper.calls.get())
                .as("EventStoreRetry 应至少触发一次退避以处理 uk_position 冲突")
                .isGreaterThanOrEqualTo(1);
        // 验证最终落库成功
        assertThat(faultStore.read("agg-1")).hasSize(1);

        faultJDBI.useHandle(handle ->
                handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

    @Test
    void append_空事件列表_不触发Sleeper_不落库() {
        eventStore.append("agg-1", List.of(), 0L);

        assertThat(sleeper.calls.get()).isZero();
        assertThat(eventStore.read("agg-1")).isEmpty();
    }

    /**
     * 测试事件 POJO。
     */
    record TestEvent(String name, long value) {
    }
}

