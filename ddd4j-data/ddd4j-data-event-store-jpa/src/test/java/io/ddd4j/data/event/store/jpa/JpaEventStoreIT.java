/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.jpa;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 强类型 {@link JpaEventStore} 的 H2 集成测试。
 */
class JpaEventStoreIT {

    private static final String ORDER_TYPE = "Order";
    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private EventStore eventStore;

    @BeforeAll
    static void createEntityManagerFactory() {
        Configuration configuration = new Configuration();
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:eventstore_typed;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.connection.username", "sa");
        configuration.setProperty("hibernate.connection.password", "");
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");
        configuration.addAnnotatedClass(StoredEventEntity.class);
        entityManagerFactory = configuration.buildSessionFactory();
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new JpaEventStore(entityManager);
    }

    @AfterEach
    void tearDown() {
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();
        entityManager.createQuery("DELETE FROM StoredEventEntity").executeUpdate();
        transaction.commit();
        entityManager.close();
    }

    @Test
    void appendAndReadShouldPreserveTypedAggregateAndPayload() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, Arrays.asList(event), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(1, events.size());
        assertEquals(ORDER_TYPE, events.get(0).aggregateType());
        assertEquals(orderId.asString(), events.get(0).aggregateId().asString());
        assertTrue(events.get(0).payload() instanceof OrderCreatedEvent);
        assertEquals(event.getEventId(), events.get(0).eventId());
    }

    @Test
    void appendWithStaleVersionShouldRollbackAndExposeTypedConflict() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-2");
        eventStore.append(ORDER_TYPE, orderId, Arrays.asList(new OrderCreatedEvent(orderId)), 0);

        assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId, Arrays.asList(new OrderCreatedEvent(orderId)), 0));

        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
    }

    @Test
    void readWithVersionRangeShouldReturnInclusiveEvents() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-3");
        eventStore.append(ORDER_TYPE, orderId,
                Arrays.asList(new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId, 1, 2);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).version());
        assertEquals(2L, events.get(1).version());
    }

    final static class TestAggregateRootId implements AggregateRootId {

        private static final long serialVersionUID = 0L;

        private final String value;

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

        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
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

    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        OrderCreatedEvent() {
            super();
        }

        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
