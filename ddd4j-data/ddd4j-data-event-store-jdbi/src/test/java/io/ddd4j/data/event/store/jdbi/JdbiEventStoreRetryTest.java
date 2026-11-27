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

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";
    /** \u8fde\u63a5\u6d4b\u8bd5\u6216\u5e94\u7528\u6570\u636e\u5e93\u7684 JDBC URL\u3002 */
    private String jdbcUrl;
    /** \u8d1f\u8d23\u83b7\u53d6 JDBC \u8fde\u63a5\u5e76\u6267\u884c SQL \u64cd\u4f5c\u7684\u8bbf\u95ee\u5165\u53e3\u3002 */
    private Jdbi jdbi;
    /** \u8bb0\u5f55\u3001\u66ff\u4ee3\u6216\u63a7\u5236\u6d4b\u8bd5\u9000\u907f\u7b49\u5f85\u7684\u7ec4\u4ef6\u3002 */
    private RecordingSleeper sleeper;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>jdbi</code>\u3001\u521d\u59cb\u5316<code>sleeper</code>\u3001\u521d\u59cb\u5316<code>eventStore</code>\u3002 */
    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:mem:jdbi_retry_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        jdbi = Jdbi.create(jdbcUrl);
        sleeper = new RecordingSleeper();
        eventStore = new JdbiEventStore(jdbi, new EventStoreRetry(5, 1L, sleeper));
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>jdbi.useHandle()</code>\u3001<code>handle.execute()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        jdbi.useHandle(handle -> handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

    /** \u9a8c\u8bc1<code>eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new TestEvent(orderId)), 0)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>sleeper.calls.get()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>eventStore.read(ORDER_TYPE, orderId).size()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void aggregateVersionConflictMustNotRetry() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new TestEvent(orderId)), 0);

        assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new TestEvent(orderId)), 0));

        assertEquals(0, sleeper.calls.get());
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
    }

    /** \u9a8c\u8bc1<code>sleeper.calls.get() &gt;= 1</code>\u4e3a\u771f\uff0c<code>eventStore.read(ORDER_TYPE, orderId).size()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.append(ORDER_TYPE, orderId, Arrays.asList(first, invalid), 1)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>successfulInserts.get()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>events.get(2).position()</code> \u7b49\u4e8e <code>3L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>insertError.get()</code>\u975e\u7a7a\uff0c<code>insertError.get().getSQLState()</code> \u7b49\u4e8e <code>"23505"</code>\uff0c<code>events.get(2).position()</code> \u7b49\u4e8e <code>3L</code>\u3002 */
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

    /** \u6536\u96c6\u6536\u5230\u7684\u7b49\u5f85\u65f6\u957f\uff0c\u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u91cd\u8bd5\u95f4\u9694\u3002 */
    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        /** \u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528\u3002 */
        private final AtomicInteger calls = new AtomicInteger();

        /**
         * \u8c03\u7528 <code>calls.incrementAndGet()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param millis \u7b49\u5f85\u6216\u63a8\u8fdb\u7684\u6beb\u79d2\u6570
         */
        @Override
        public void sleep(long millis) {
            calls.incrementAndGet();
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    final static class TestAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u9886\u57df\u4e8b\u4ef6\uff0c\u7528\u4e8e\u63a7\u5236\u4e8b\u4ef6\u7c7b\u578b\u548c\u8d1f\u8f7d\u3002 */
    static final class TestEvent extends DomainEvent<TestAggregateRootId> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        TestEvent() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param orderId \u6d4b\u8bd5\u4e2d\u8981\u5b9a\u4f4d\u7684\u8ba2\u5355\u6807\u8bc6
         */
        TestEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
