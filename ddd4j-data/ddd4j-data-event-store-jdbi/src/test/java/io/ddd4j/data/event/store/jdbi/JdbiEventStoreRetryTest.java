/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.jdbi;

import java.util.Arrays;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
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
 * 强类型 JDBI EventStore 的 position 冲突重试集成测试。
 */
class JdbiEventStoreRetryTest {

    private static final String ORDER_TYPE = "Order";
    private Jdbi jdbi;
    private RecordingSleeper sleeper;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create("jdbc:h2:mem:jdbi_retry_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
        eventStore.append(ORDER_TYPE, orderId, Arrays.asList(new TestEvent(orderId)), 0);

        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId, Arrays.asList(new TestEvent(orderId)), 0))
                .isInstanceOf(AggregateVersionConflictException.class);

        assertThat(sleeper.calls.get()).isZero();
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    @Test
    void positionConflictShouldRetryAndPersistTypedEvent() {
        AtomicInteger insertCount = new AtomicInteger();
        jdbi.getConfig(SqlStatements.class).setSqlLogger(new org.jdbi.v3.core.statement.SqlLogger() {
            @Override
            public void logBeforeExecution(StatementContext context) {
                if (context.getRenderedSql().toUpperCase().startsWith("INSERT") && insertCount.incrementAndGet() == 1) {
                    throw new RuntimeException(new SQLIntegrityConstraintViolationException("Unique index uk_position violation"));
                }
            }
        });
        TestAggregateRootId orderId = new TestAggregateRootId("order-2");

        eventStore.append(ORDER_TYPE, orderId, Arrays.asList(new TestEvent(orderId)), 0);

        assertThat(sleeper.calls.get()).isGreaterThanOrEqualTo(1);
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    static final class RecordingSleeper implements EventStoreRetry.Sleeper {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void sleep(long millis) {
            calls.incrementAndGet();
        }
    }

    record TestAggregateRootId(String value) implements AggregateRootId {
        private static final EntityType TYPE = new StringEntityType("Order");

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
