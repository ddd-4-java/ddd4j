/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.r2dbc;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityIdRegistry;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.r2dbc.h2.CloseableConnectionFactory;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.h2.H2ConnectionOption;
import io.r2dbc.spi.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2DBC 同步边界的 H2 强类型契约测试。
 */
class R2dbcEventStoreTest {

    private static final String ORDER_TYPE = "Order";
    private CloseableConnectionFactory connectionFactory;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        connectionFactory = H2ConnectionFactory.inMemory(
                "sync_eventstore_" + System.nanoTime(), "sa", "",
                Map.of(H2ConnectionOption.DB_CLOSE_DELAY, "-1"));
        EntityIdRegistry.register(TestAggregateRootId.TYPE_NAME, TestAggregateRootId::new);
        eventStore = new R2dbcEventStore(connectionFactory);
    }

    @AfterEach
    void tearDown() {
        EntityIdRegistry.unregister(TestAggregateRootId.TYPE_NAME);
        Connection connection = Mono.from(connectionFactory.create()).block();
        if (connection != null) {
            Mono.from(connection.createStatement("DROP TABLE IF EXISTS DDD4J_EVENT_STORE").execute()).block();
            Mono.from(connection.close()).block();
        }
    }

    @Test
    void appendAndReadShouldPreserveTypedMetadata() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(0).eventId()).isEqualTo(event.getEventId());
    }

    @Test
    void versionRangeAndAggregateTypeIsolationShouldFollowCoreContract() {
        TestAggregateRootId orderId = new TestAggregateRootId("shared");
        eventStore.append(ORDER_TYPE, orderId,
                List.of(new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0);
        eventStore.append("Invoice", orderId, List.of(new OrderCreatedEvent(orderId)), 0);

        assertThat(eventStore.read(ORDER_TYPE, orderId, 1, 2))
                .extracting(StoredEvent::version)
                .containsExactly(1L, 2L);
        assertThat(eventStore.read("Invoice", orderId)).hasSize(1);
        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0))
                .isInstanceOf(AggregateVersionConflictException.class);
    }

    static final class TestAggregateRootId implements AggregateRootId {
        private static final String TYPE_NAME = "R2dbcOrder";
        private static final EntityType TYPE = new StringEntityType(TYPE_NAME);
        private final String value;

        TestAggregateRootId(String value) {
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
            return TYPE_NAME + ":" + value;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof TestAggregateRootId other && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {
        OrderCreatedEvent() {
            super();
        }

        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
