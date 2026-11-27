/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.jdbi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 强类型 JDBI EventStore 的 position 冲突重试集成测试。
 */
class JdbiEventStoreRetryTest {

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";
    /**
     * 连接测试或应用数据库的 JDBC URL。
     */
    private String jdbcUrl;
    /**
     * 负责获取 JDBC 连接并执行 SQL 操作的访问入口。
     */
    private Jdbi jdbi;
    /**
     * 记录、替代或控制测试退避等待的组件。
     */
    private RecordingSleeper sleeper;
    /**
     * 读取及追加领域事件的事件存储。
     */
    private EventStore eventStore;

    /**
     * 测试前依次初始化<code>jdbi</code>、初始化<code>sleeper</code>、初始化<code>eventStore</code>。
     */
    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:mem:jdbi_retry_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        jdbi = Jdbi.create(jdbcUrl);
        sleeper = new RecordingSleeper();
        eventStore = new JdbiEventStore(jdbi, new EventStoreRetry(5, 1L, sleeper));
    }

    /**
     * 测试结束后执行 <code>jdbi.useHandle()</code>、<code>handle.execute()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        jdbi.useHandle(handle -> handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

    /**
     * 验证<code>eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new TestEvent(orderId)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>sleeper.calls.get()</code> 等于 <code>0</code>，<code>eventStore.read(ORDER_TYPE, orderId).size()</code> 等于 <code>1</code>。
     */
    @Test
    void aggregateVersionConflictMustNotRetry() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new TestEvent(orderId)), 0);

        assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new TestEvent(orderId)), 0));

        assertEquals(0, sleeper.calls.get());
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
    }

    /**
     * 验证<code>sleeper.calls.get() &gt;= 1</code>为真，<code>eventStore.read(ORDER_TYPE, orderId).size()</code> 等于 <code>1</code>。
     */
    @Test
    void positionConflictShouldRetryAndPersistTypedEvent() {
        AtomicInteger insertCount = new AtomicInteger();
        jdbi.getConfig(SqlStatements.class).setSqlLogger(new SqlLogger() {
            @Override
            public void logBeforeExecution(StatementContext context) {
                if (context.getRenderedSql().toUpperCase().startsWith("INSERT") && insertCount.incrementAndGet() == 1) {
                    throw new RuntimeException(new SQLIntegrityConstraintViolationException("Unique index uk_position violation"));
                }
            }
        });
        TestAggregateRootId orderId = new TestAggregateRootId("order-2");

        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new TestEvent(orderId)), 0);

        assertTrue(sleeper.calls.get() >= 1);
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
    }

    /**
     * 验证<code>eventStore.append(ORDER_TYPE, orderId, Arrays.asList(first, invalid), 1)</code> 抛出 <code>IllegalArgumentException.class</code>，<code>successfulInserts.get()</code> 等于 <code>1</code>，<code>events.get(2).position()</code> 等于 <code>3L</code>。
     */
    @Test
    void secondEventFailureRollsBackFirstInsertAndKeepsVersionAndPositionAvailable() {
        TestAggregateRootId orderId = new TestAggregateRootId("partly-executed-batch");
        TestEvent committed = new TestEvent(orderId);
        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(committed), 0);

        AtomicInteger successfulInserts = new AtomicInteger();
        jdbi.getConfig(SqlStatements.class).setSqlLogger(new SqlLogger() {
            @Override
            public void logAfterExecution(StatementContext context) {
                if (context.getRenderedSql().startsWith("INSERT")) {
                    successfulInserts.incrementAndGet();
                }
            }
        });
        TestEvent first = new TestEvent(orderId);
        IllegalArgumentException invalidEventId = new IllegalArgumentException("invalid event id");
        DomainEvent<TestAggregateRootId> invalid = new DomainEvent<TestAggregateRootId>(new EntityIdPath(orderId)) {
            @Override
            public EventId getEventId() {
                throw invalidEventId;
            }
        };

        assertSame(invalidEventId, assertThrows(IllegalArgumentException.class,
                () -> eventStore.append(ORDER_TYPE, orderId, Arrays.asList(first, invalid), 1)));
        assertEquals(1, successfulInserts.get(), "first event reached the database before the failure");
        assertEquals(0, sleeper.calls.get(), "invalid data must not be retried");
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
        assertEquals(1, eventStore.readAll(1, 10).size());

        TestEvent second = new TestEvent(orderId);
        eventStore.append(ORDER_TYPE, orderId, Arrays.asList(first, second), 1);
        List<StoredEvent> events = eventStore.readAll(1, 10);
        assertEquals(3, events.size());
        assertEquals(committed.getEventId(), events.get(0).eventId());
        assertEquals(first.getEventId(), events.get(1).eventId());
        assertEquals(second.getEventId(), events.get(2).eventId());
        assertEquals(2L, events.get(1).version());
        assertEquals(2L, events.get(1).position());
        assertEquals(3L, events.get(2).version());
        assertEquals(3L, events.get(2).position());
    }

    /**
     * 验证<code>insertError.get()</code>非空，<code>insertError.get().getSQLState()</code> 等于 <code>"23505"</code>，<code>events.get(2).position()</code> 等于 <code>3L</code>。
     */
    @Test
    void databasePositionCollisionRetriesWholeBatchAgainstCommittedPosition() {
        TestAggregateRootId orderId = new TestAggregateRootId("batch-after-collision");
        TestAggregateRootId competingId = new TestAggregateRootId("independent-transaction");
        EventStore competingStore = new JdbiEventStore(Jdbi.create(jdbcUrl));
        competingStore.read(ORDER_TYPE, competingId);
        TestEvent competingEvent = new TestEvent(competingId);
        AtomicInteger insertAttempts = new AtomicInteger();
        AtomicReference<SQLException> insertError = new AtomicReference<>();
        jdbi.getConfig(SqlStatements.class).setSqlLogger(new SqlLogger() {
            @Override
            public void logBeforeExecution(StatementContext context) {
                if (context.getRenderedSql().startsWith("INSERT") && insertAttempts.incrementAndGet() == 1) {
                    // The first transaction has chosen its position. Commit another connection's write first.
                    competingStore.append(ORDER_TYPE, competingId, Collections.singletonList(competingEvent), 0);
                }
            }

            @Override
            public void logException(StatementContext context, SQLException failure) {
                if (context.getRenderedSql().startsWith("INSERT")) {
                    insertError.set(failure);
                }
            }
        });
        TestEvent first = new TestEvent(orderId);
        TestEvent second = new TestEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, Arrays.asList(first, second), 0);

        assertNotNull(insertError.get(), "H2 must reject the stale global position");
        assertEquals("23505", insertError.get().getSQLState());
        assertEquals(1, sleeper.calls.get());
        assertEquals(3, insertAttempts.get(), "one rejected INSERT and both batch events retried");
        List<StoredEvent> events = eventStore.readAll(1, 10);
        assertEquals(3, events.size());
        assertEquals(competingEvent.getEventId(), events.get(0).eventId());
        assertEquals(1L, events.get(0).position());
        assertEquals(first.getEventId(), events.get(1).eventId());
        assertEquals(1L, events.get(1).version());
        assertEquals(2L, events.get(1).position());
        assertEquals(second.getEventId(), events.get(2).eventId());
        assertEquals(2L, events.get(2).version());
        assertEquals(3L, events.get(2).position());
    }

    /**
     * 收集收到的等待时长，供测试验证重试间隔。
     */
    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        /**
         * 记录被测入口实际触发的调用。
         */
        private final AtomicInteger calls = new AtomicInteger();

        /**
         * 调用 <code>calls.incrementAndGet()</code> 执行当前夹具操作。
         *
         * @param millis 等待或推进的毫秒数
         */
        @Override
        public void sleep(long millis) {
            calls.incrementAndGet();
        }
    }

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    final static class TestAggregateRootId implements AggregateRootId {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;
        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Order");
        /**
         * 用于组成测试标识的原始值。
         */
        private final String value;

        /**
         * 以给定状态构造 <code>TestAggregateRootId</code> 测试对象。
         *
         * @param value 用于组成测试标识的原始值
         */
        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * <p>测试夹具会调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 实际返回<code>TYPE.asString() + ":" + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 读取<code>value</code> 字段。
         *
         * @return <code>value</code> 字段
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TestAggregateRootId other = (TestAggregateRootId) obj;
            return Objects.equals(this.value, other.value);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    /**
     * 测试专用领域事件，用于控制事件类型和负载。
     */
    static final class TestEvent extends DomainEvent<TestAggregateRootId> {
        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         */
        TestEvent() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         *
         * @param orderId 测试中要定位的订单标识
         */
        TestEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
