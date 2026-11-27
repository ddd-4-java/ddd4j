/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.panache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Panache EventStore 的 H2 强类型契约测试。
 */
class PanacheEventStoreTest {

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";

    /** \u7528\u4e8e\u521b\u5efa JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u7684\u5de5\u5382\u3002 */
    private EntityManagerFactory entityManagerFactory;
    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private EntityManager entityManager;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>System.nanoTime()</code>\u3001<code>configuration.buildSessionFactory()</code>\u3001<code>entityManagerFactory.createEntityManager()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
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

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>entityManager.isOpen()</code>\u3001<code>entityManager.close()</code>\u3001<code>entityManagerFactory.isOpen()</code>\u3001<code>entityManagerFactory.close()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
        }
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>events.get(0).aggregateType()</code> \u7b49\u4e8e <code>ORDER_TYPE</code>\uff0c<code>events.get(0).eventId()</code> \u7b49\u4e8e <code>event.getEventId()</code>\u3002 */
    @Test
    void appendAndReadShouldPreserveTypedEventMetadata() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId()).isInstanceOf(AggregateRootId.class);
        assertThat(events.get(0).aggregateId().asString()).isEqualTo(orderId.asString());
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(0).eventId()).isEqualTo(event.getEventId());
    }

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, orderId).clear()</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>eventStore.read(ORDER_TYPE, orderId, 1, 1).clear()</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>eventStore.readAll(0, 10).clear()</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void readResultsShouldBeUnmodifiableSnapshots() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-immutable");
        eventStore.append(ORDER_TYPE, orderId, java.util.Arrays.asList(new OrderCreatedEvent(orderId)), 0);

        assertThatThrownBy(() -> eventStore.read(ORDER_TYPE, orderId).clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> eventStore.read(ORDER_TYPE, orderId, 1, 1).clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> eventStore.readAll(0, 10).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** \u9a8c\u8bc1<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\u3002 */
    @Test
    void readWithVersionRangeAndConflictShouldFollowCoreContract() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-2");
        eventStore.append(ORDER_TYPE, orderId,
                List.of(new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0);

        assertThat(eventStore.read(ORDER_TYPE, orderId, 1, 2))
                .extracting(StoredEvent::version)
                .containsExactly(1L, 2L);
        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0))
                .isInstanceOf(AggregateVersionConflictException.class);
    }

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>events.get(0).position()</code> \u5c0f\u4e8e <code>events.get(1).position()</code>\uff0c<code>eventStore.readAll(events.get(1).position(), 10)</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void readAllShouldRemainPositionOrderedAcrossAggregateTypes() {
        TestAggregateRootId firstId = new TestAggregateRootId("order-3");
        TestAggregateRootId secondId = new TestAggregateRootId("order-4");
        eventStore.append(ORDER_TYPE, firstId, List.of(new OrderCreatedEvent(firstId)), 0);
        eventStore.append("Invoice", secondId, List.of(new OrderCreatedEvent(secondId)), 0);

        List<StoredEvent> events = eventStore.readAll(0, 10);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).position()).isLessThan(events.get(1).position());
        assertThat(eventStore.readAll(events.get(1).position(), 10)).hasSize(1);
    }

    /** \u9a8c\u8bc1<code>eventStore.append( ORDER_TYPE, orderId, Collections.singletonList(new OrderCreatedEvent(orderId)), 1)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>committed.get(0).eventId()</code> \u7b49\u4e8e <code>original.getEventId()</code>\uff0c<code>committed.get(0).correlationId()</code> \u7b49\u4e8e <code>callerCorrelation</code>\u3002 */
    @Test
    void appendRejectionShouldLeaveCallerTransactionAndFlushedChangesIntact() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-caller-transaction");
        OrderCreatedEvent original = new OrderCreatedEvent(orderId);
        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(original), 0);

        EntityTransaction caller = entityManager.getTransaction();
        caller.begin();
        try {
            PanacheStoredEventEntity existing = entityManager.createQuery(
                    "SELECT e FROM PanacheStoredEventEntity e", PanacheStoredEventEntity.class).getSingleResult();
            EventId callerCorrelation = new OrderCreatedEvent(orderId).getEventId();
            existing.correlationId = callerCorrelation.asString();
            entityManager.flush();

            assertThatThrownBy(() -> eventStore.append(
                    ORDER_TYPE, orderId, Collections.singletonList(new OrderCreatedEvent(orderId)), 1))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(caller.isActive()).as("caller transaction survives rejected append").isTrue();
            caller.commit();

            entityManager.clear();
            List<StoredEvent> committed = eventStore.read(ORDER_TYPE, orderId);
            assertThat(committed).extracting(StoredEvent::version).containsExactly(1L);
            assertThat(committed.get(0).eventId()).isEqualTo(original.getEventId());
            assertThat(committed.get(0).correlationId()).isEqualTo(callerCorrelation);
        } finally {
            if (caller.isActive()) {
                caller.rollback();
            }
        }
    }

    /** \u9a8c\u8bc1<code>entityManager.getTransaction().isActive()</code>\u4e3a\u5047\uff0c<code>eventStore.read(ORDER_TYPE, orderId)</code>\u4e3a\u7a7a\uff0c<code>eventStore.readAll(0, 10)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void failureAfterFlushingFirstEventShouldRollBackEntireBatch() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-failed-batch");
        OrderCreatedEvent first = new OrderCreatedEvent(orderId);
        IllegalArgumentException failure = new IllegalArgumentException("invalid second event");
        DomainEvent<TestAggregateRootId> invalid = new DomainEvent<TestAggregateRootId>(new EntityIdPath(orderId)) {
            @Override
            public EventId getEventId() {
                entityManager.flush();
                throw failure;
            }
        };

        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId, Arrays.asList(first, invalid), 0))
                .isSameAs(failure);
        assertThat(entityManager.getTransaction().isActive()).isFalse();
        assertThat(eventStore.read(ORDER_TYPE, orderId)).isEmpty();
        assertThat(eventStore.readAll(0, 10)).isEmpty();

        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(first), 0);
        assertThat(eventStore.readAll(0, 10)).extracting(StoredEvent::position).containsExactly(1L);
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    final static class TestAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
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

    /** \u8868\u793a\u8ba2\u5355\u521b\u5efa\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u9a8c\u8bc1\u4e8b\u4ef6\u5904\u7406\u94fe\u3002 */
    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderCreatedEvent() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param orderId \u6d4b\u8bd5\u4e2d\u8981\u5b9a\u4f4d\u7684\u8ba2\u5355\u6807\u8bc6
         */
        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
