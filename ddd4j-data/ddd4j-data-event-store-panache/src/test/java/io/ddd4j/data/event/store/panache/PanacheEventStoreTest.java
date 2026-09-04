/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.panache;

import java.util.Collections;
import java.util.Arrays;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Panache EventStore 的 H2 强类型契约测试。
 */
class PanacheEventStoreTest {

    private static final String ORDER_TYPE = "Order";

    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url",
                        "jdbc:h2:mem:panache_typed_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .addAnnotatedClass(PanacheStoredEventEntity.class);
        entityManagerFactory = configuration.buildSessionFactory();
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new PanacheEventStore(entityManager);
    }

    @AfterEach
    void tearDown() {
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
        }
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    @Test
    void appendAndReadShouldPreserveTypedEventMetadata() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(event), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId()).isInstanceOf(AggregateRootId.class);
        assertThat(events.get(0).aggregateId().asString()).isEqualTo(orderId.asString());
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(0).eventId()).isEqualTo(event.getEventId());
    }

    @Test
    void readWithVersionRangeAndConflictShouldFollowCoreContract() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-2");
        eventStore.append(ORDER_TYPE, orderId,
                Arrays.asList(new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0);

        assertThat(eventStore.read(ORDER_TYPE, orderId, 1, 2))
                .extracting(StoredEvent::version)
                .containsExactly(1L, 2L);
        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId, Arrays.asList(new OrderCreatedEvent(orderId)), 0))
                .isInstanceOf(AggregateVersionConflictException.class);
    }

    @Test
    void readAllShouldRemainPositionOrderedAcrossAggregateTypes() {
        TestAggregateRootId firstId = new TestAggregateRootId("order-3");
        TestAggregateRootId secondId = new TestAggregateRootId("order-4");
        eventStore.append(ORDER_TYPE, firstId, Arrays.asList(new OrderCreatedEvent(firstId)), 0);
        eventStore.append("Invoice", secondId, Arrays.asList(new OrderCreatedEvent(secondId)), 0);

        List<StoredEvent> events = eventStore.readAll(0, 10);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).position()).isLessThan(events.get(1).position());
        assertThat(eventStore.readAll(events.get(1).position(), 10)).hasSize(1);
    }static final class TestAggregateRootId implements AggregateRootId {
        private final String value;

        public TestAggregateRootId(String value) {
            this.value = value;
        }
        public String value() { return value; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestAggregateRootId)) return false;
            TestAggregateRootId other = (TestAggregateRootId) o;
            return Objects.equals(this.value, other.value);
        }
        @Override
        public int hashCode() {
            return java.util.Objects.hash(value);
        }
        @Override
        public String toString() {
            return "TestAggregateRootId{" + "value=" + value + "}";
        }
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

    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {
        OrderCreatedEvent() {
            super();
        }

        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
