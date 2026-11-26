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

    private static final String ORDER_TYPE = "Order";
    private String jdbcUrl;
    private Jdbi jdbi;
    private RecordingSleeper sleeper;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:mem:jdbi_retry_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        jdbi = Jdbi.create(jdbcUrl);
        sleeper = new RecordingSleeper();
        eventStore = new JdbiEventStore(jdbi, new EventStoreRetry(5, 1L, sleeper));
    }

    @AfterEach
    void tearDown() {
        jdbi.useHandle(handle -> handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

    @Test
    void aggregateVersionConflictMustNotRetry() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new TestEvent(orderId)), 0);

        assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new TestEvent(orderId)), 0));

        assertEquals(0, sleeper.calls.get());
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
    }

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

    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void sleep(long millis) {
            calls.incrementAndGet();
        }
    }

    final static class TestAggregateRootId implements AggregateRootId {

        private static final long serialVersionUID = 0L;
        private static final EntityType TYPE = new StringEntityType("Order");
        private final String value;

        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        @JsonProperty("value")
        public String value() {
            return value;
        }

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

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    static final class TestEvent extends DomainEvent<TestAggregateRootId> {
        TestEvent() {
            super();
        }

        TestEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
