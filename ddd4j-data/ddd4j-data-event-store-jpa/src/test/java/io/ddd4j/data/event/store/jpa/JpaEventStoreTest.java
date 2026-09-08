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
package io.ddd4j.data.event.store.jpa;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaEventStoreTest {

    private static final String ORDER_TYPE = "Order";

    private static EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private EventStore eventStore;

    @BeforeAll
    static void createEntityManagerFactory() {
        Configuration configuration = new Configuration();
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:jpa-event-store-test;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.connection.username", "sa");
        configuration.setProperty("hibernate.connection.password", "");
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        configuration.setProperty("hibernate.show_sql", "false");
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
        EntityTransaction tx = entityManager.getTransaction();
        if (!tx.isActive()) {
            tx.begin();
        }
        entityManager.createQuery("delete from StoredEventEntity").executeUpdate();
        tx.commit();
        entityManager.close();
    }

    @Test
    void appendAndReadShouldRoundTripBusinessPayload() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-a");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("created"), new OrderCreatedEvent("renamed")), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).version());
        assertEquals(2L, events.get(1).version());
        assertInstanceOf(OrderCreatedEvent.class, events.get(0).payload());
        assertEquals("created", ((OrderCreatedEvent) events.get(0).payload()).getFact());
        assertEquals("renamed", ((OrderCreatedEvent) events.get(1).payload()).getFact());
        assertEquals(orderId.asString(), events.get(0).aggregateId().asString());
    }

    @Test
    void appendWithStaleVersionShouldReject() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-b");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("first")), 0);

        AggregateVersionConflictException conflict = assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId,
                        Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("second")), 0));
        assertEquals(0L, conflict.expectedVersion());
        assertEquals(1L, conflict.actualVersion());
    }

    @Test
    void readVersionRangeShouldReturnInclusiveSlice() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-c");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("v1"), new OrderCreatedEvent("v2"), new OrderCreatedEvent("v3")), 0);

        List<StoredEvent> slice = eventStore.read(ORDER_TYPE, orderId, 2, 3);
        assertEquals(2, slice.size());
        assertEquals(2L, slice.get(0).version());
        assertEquals(3L, slice.get(1).version());
    }

    @Test
    void readAllShouldPreserveGlobalPositionOrderAcrossAggregates() {
        TestAggregateRootId first = new TestAggregateRootId("order-d");
        TestAggregateRootId second = new TestAggregateRootId("order-e");
        eventStore.append(ORDER_TYPE, first,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("d")), 0);
        eventStore.append(ORDER_TYPE, second,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("e")), 0);

        List<StoredEvent> events = eventStore.readAll(1, 10);
        assertTrue(events.size() >= 2);
        assertEquals(first.asString(), events.get(events.size() - 2).aggregateId().asString());
        assertEquals(second.asString(), events.get(events.size() - 1).aggregateId().asString());
        assertTrue(events.get(events.size() - 2).position() < events.get(events.size() - 1).position());
    }

    @Test
    void causalityColumnsShouldRoundTrip() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-f");
        OrderCreatedEvent cause = new OrderCreatedEvent("cause");
        OrderCreatedEvent effect = new OrderCreatedEvent("effect", cause);
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(cause, effect), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertNull(events.get(0).correlationId());
        assertEquals(cause.getEventId(), events.get(1).correlationId());
        assertEquals(cause.getEventId(), events.get(1).causationId());
    }

    @Test
    void readMissingStreamShouldReturnEmpty() {
        assertEquals(0, eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing")).size());
    }

    @Test
    void appendMustNotMutateInputAggregateVersion() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-input");
        OrderCreatedEvent event = new OrderCreatedEvent("input");

        assertNull(event.getAggregateVersion());
        eventStore.append(ORDER_TYPE, orderId, Collections.<DomainEvent<?>>singletonList(event), 0);

        assertNull(event.getAggregateVersion());
    }

    @Test
    void batchAppendMustWorkWithCommitFlushMode() {
        entityManager.setFlushMode(javax.persistence.FlushModeType.COMMIT);
        TestAggregateRootId orderId = new TestAggregateRootId("commit-flush-batch");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("first"), new OrderCreatedEvent("second")), 0);
        entityManager.clear();
        List<StoredEvent> stored = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(2, stored.size());
        assertTrue(stored.get(0).position() < stored.get(1).position());
        assertEquals(javax.persistence.FlushModeType.COMMIT, entityManager.getFlushMode());
    }

    @Test
    void customRepositoryMustControlVersionConflict() {
        JpaStoredEventRepository custom = new JpaStoredEventRepositoryImpl(entityManager) {
            @Override
            public long findCurrentVersion(String aggregateType, String aggregateId) {
                return 7L;
            }
        };
        EventStore customized = new JpaEventStore(entityManager, custom);
        AggregateVersionConflictException conflict = assertThrows(AggregateVersionConflictException.class,
                () -> customized.append(ORDER_TYPE, new TestAggregateRootId("custom-repository"),
                        Collections.singletonList(new OrderCreatedEvent("event")), 0));
        assertEquals(7L, conflict.actualVersion());
        assertEquals(0L, conflict.expectedVersion());
    }

    @Test
    void customRepositoryMustControlPositionAndPersistence() {
        JpaStoredEventRepository custom = new JpaStoredEventRepositoryImpl(entityManager) {
            @Override
            public long nextPosition() {
                return 42L;
            }

            @Override
            public void save(StoredEventEntity entity) {
                entity.setAggregateId("routed-stream");
                super.save(entity);
            }
        };
        EventStore customized = new JpaEventStore(entityManager, custom);
        customized.append(ORDER_TYPE, new TestAggregateRootId("source-stream"),
                Collections.singletonList(new OrderCreatedEvent("custom-persistence")), 0);
        entityManager.clear();

        assertTrue(eventStore.read(ORDER_TYPE, new TestAggregateRootId("source-stream")).isEmpty());
        List<StoredEvent> stored = eventStore.read(ORDER_TYPE, new TestAggregateRootId("routed-stream"));
        assertEquals(1, stored.size());
        assertEquals(42L, stored.get(0).position());
        assertEquals("custom-persistence", ((OrderCreatedEvent) stored.get(0).payload()).getFact());
    }

    @Test
    void customRepositoryMustControlAllReadPaths() {
        TestAggregateRootId orderId = new TestAggregateRootId("filtered-stream");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.singletonList(new OrderCreatedEvent("stored")), 0);
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
        JpaStoredEventRepository custom = new JpaStoredEventRepositoryImpl(entityManager) {
            @Override
            public List<StoredEventEntity> findByAggregateTypeAndAggregateIdOrderByVersionAsc(
                    String aggregateType, String aggregateId) {
                return Collections.emptyList();
            }

            @Override
            public List<StoredEventEntity> findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(
                    String aggregateType, String aggregateId, long fromVersion, long toVersion) {
                return Collections.emptyList();
            }

            @Override
            public List<StoredEventEntity> findByPositionGreaterThanEqualOrderByPositionAsc(
                    long fromPosition, int limit) {
                return Collections.emptyList();
            }
        };
        EventStore customized = new JpaEventStore(entityManager, custom);
        assertTrue(customized.read(ORDER_TYPE, orderId).isEmpty());
        assertTrue(customized.read(ORDER_TYPE, orderId, 1, 1).isEmpty());
        assertTrue(customized.readAll(0, 10).isEmpty());
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
    }

    @Test
    void failedBatchMustPreserveCommittedStateAndAllowNextAppend() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-rollback");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.singletonList(new OrderCreatedEvent("committed")), 0);

        assertThrows(RuntimeException.class, () -> eventStore.append(ORDER_TYPE, orderId,
                Arrays.<DomainEvent<?>>asList(new OrderCreatedEvent("must-rollback"), null), 1));
        entityManager.clear();

        List<StoredEvent> afterFailure = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(1, afterFailure.size());
        assertEquals("committed", ((OrderCreatedEvent) afterFailure.get(0).payload()).getFact());
        assertEquals(1L, afterFailure.get(0).version());

        eventStore.append(ORDER_TYPE, orderId,
                Collections.singletonList(new OrderCreatedEvent("after-failure")), 1);
        entityManager.clear();
        List<StoredEvent> recovered = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(2, recovered.size());
        assertEquals("after-failure", ((OrderCreatedEvent) recovered.get(1).payload()).getFact());
        assertEquals(2L, recovered.get(1).version());
    }

    @Test
    void rejectedNestedAppendMustNotRollbackCallerTransaction() {
        EntityTransaction callerTransaction = entityManager.getTransaction();
        callerTransaction.begin();
        try {
            assertThrows(IllegalStateException.class, () -> eventStore.append(ORDER_TYPE,
                    new TestAggregateRootId("caller-transaction"),
                    Collections.singletonList(new OrderCreatedEvent("rejected")), 0));
            assertTrue(callerTransaction.isActive(), "EventStore must not rollback a caller-owned transaction");
            assertThrows(IllegalStateException.class, () -> eventStore.read(ORDER_TYPE,
                    new TestAggregateRootId("caller-transaction")));
            assertTrue(callerTransaction.isActive());
            assertThrows(IllegalStateException.class, () -> eventStore.read(ORDER_TYPE,
                    new TestAggregateRootId("caller-transaction"), 1, 2));
            assertTrue(callerTransaction.isActive());
            assertThrows(IllegalStateException.class, () -> eventStore.readAll(0, 10));
            assertTrue(callerTransaction.isActive());
        } finally {
            if (callerTransaction.isActive()) {
                callerTransaction.rollback();
            }
        }
    }

    @Test
    void participatingAppendMustLeaveCommitToCaller() {
        JpaEventStore participating = JpaEventStore.participating(entityManager);
        TestAggregateRootId orderId = new TestAggregateRootId("external-commit");
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            participating.append(ORDER_TYPE, orderId,
                    Collections.singletonList(new OrderCreatedEvent("pending")), 0);
            assertTrue(tx.isActive());
            assertEquals(1, participating.read(ORDER_TYPE, orderId).size());

            tx.rollback();
            entityManager.clear();
            assertTrue(eventStore.read(ORDER_TYPE, orderId).isEmpty());
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }
    }

    @Test
    void participatingOperationsMustRejectWithoutCallerTransaction() {
        JpaEventStore participating = JpaEventStore.participating(entityManager);
        TestAggregateRootId orderId = new TestAggregateRootId("missing-caller-transaction");

        assertThrows(IllegalStateException.class, () -> participating.append(ORDER_TYPE, orderId,
                Collections.singletonList(new OrderCreatedEvent("rejected")), 0));
        assertThrows(IllegalStateException.class, () -> participating.read(ORDER_TYPE, orderId));
        assertThrows(IllegalStateException.class, () -> participating.read(ORDER_TYPE, orderId, 1, 2));
        assertThrows(IllegalStateException.class, () -> participating.readAll(0, 10));
    }

    @Test
    void participatingEmptyAppendMustRejectWithoutCallerTransaction() {
        JpaEventStore participating = JpaEventStore.participating(entityManager);

        assertThrows(IllegalStateException.class, () -> participating.append(ORDER_TYPE,
                new TestAggregateRootId("empty-batch"), Collections.emptyList(), 0));
    }

    @Test
    void participatingAppendsMustShareCallerTransactionAndCommitTogether() {
        JpaEventStore participating = JpaEventStore.participating(entityManager);
        TestAggregateRootId orderId = new TestAggregateRootId("shared-caller-transaction");
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            participating.append(ORDER_TYPE, orderId,
                    Collections.singletonList(new OrderCreatedEvent("first")), 0);
            assertTrue(tx.isActive());
            participating.append(ORDER_TYPE, orderId,
                    Collections.singletonList(new OrderCreatedEvent("second")), 1);
            assertTrue(tx.isActive());

            tx.commit();
            entityManager.clear();
            List<StoredEvent> stored = eventStore.read(ORDER_TYPE, orderId);
            assertEquals(2, stored.size());
            assertEquals("first", ((OrderCreatedEvent) stored.get(0).payload()).getFact());
            assertEquals("second", ((OrderCreatedEvent) stored.get(1).payload()).getFact());
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }
    }

    @Test
    void participatingFailedBatchMustMarkCallerTransactionRollbackOnly() {
        JpaEventStore participating = JpaEventStore.participating(entityManager);
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            assertThrows(RuntimeException.class, () -> participating.append(ORDER_TYPE,
                    new TestAggregateRootId("rollback-only"),
                    Arrays.<DomainEvent<?>>asList(new OrderCreatedEvent("partial"), null), 0));
            assertTrue(tx.isActive());
            assertTrue(tx.getRollbackOnly());
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }
    }

    @Test
    void participatingAppendMustPreserveCommitFlushMode() {
        javax.persistence.FlushModeType originalFlushMode = entityManager.getFlushMode();
        entityManager.setFlushMode(javax.persistence.FlushModeType.COMMIT);
        JpaEventStore participating = JpaEventStore.participating(entityManager);
        TestAggregateRootId orderId = new TestAggregateRootId("participating-commit-flush");
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            participating.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                    new OrderCreatedEvent("first"), new OrderCreatedEvent("second")), 0);

            assertEquals(2, participating.read(ORDER_TYPE, orderId).size());
            assertEquals(javax.persistence.FlushModeType.COMMIT, entityManager.getFlushMode());
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            entityManager.setFlushMode(originalFlushMode);
        }
    }

    @Test
    void participatingCustomRepositoryMustControlPositionAndPersistence() {
        JpaStoredEventRepository custom = new JpaStoredEventRepositoryImpl(entityManager) {
            @Override
            public long nextPosition() {
                return 84L;
            }

            @Override
            public void save(StoredEventEntity entity) {
                entity.setAggregateId("participating-routed-stream");
                super.save(entity);
            }
        };
        JpaEventStore participating = JpaEventStore.participating(entityManager, custom,
                new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            participating.append(ORDER_TYPE, new TestAggregateRootId("participating-source-stream"),
                    Collections.singletonList(new OrderCreatedEvent("custom-participating")), 0);
            assertTrue(tx.isActive());
            tx.commit();
            entityManager.clear();

            assertTrue(eventStore.read(ORDER_TYPE,
                    new TestAggregateRootId("participating-source-stream")).isEmpty());
            List<StoredEvent> stored = eventStore.read(ORDER_TYPE,
                    new TestAggregateRootId("participating-routed-stream"));
            assertEquals(1, stored.size());
            assertEquals(84L, stored.get(0).position());
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }
    }

    @Test
    void readAllLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> eventStore.readAll(0, 0));
    }

    @Test
    void persistedTimestampMustComeFromEvent() throws Exception {
        TestAggregateRootId orderId = new TestAggregateRootId("order-time");
        OrderCreatedEvent event = new OrderCreatedEvent("time");
        java.time.ZonedDateTime expected = java.time.ZonedDateTime.parse("2024-01-02T03:04:05Z");
        java.lang.reflect.Field field = DomainEvent.class.getDeclaredField("eventTimestamp");
        field.setAccessible(true);
        field.set(event, expected);

        eventStore.append(ORDER_TYPE, orderId, Collections.<DomainEvent<?>>singletonList(event), 0);

        assertEquals(expected.toInstant(), eventStore.read(ORDER_TYPE, orderId).get(0).timestamp().toInstant());
    }

    private static final class TestAggregateRootId implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType("Order");

        private final String value;

        private TestAggregateRootId(String value) {
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
    }

    /** 业务事件样例：无参构造 + JavaBean 属性（payload 序列化约定）。 */
    public static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        private String fact;

        public OrderCreatedEvent() {
            super();
        }

        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        private OrderCreatedEvent(String fact, io.ddd4j.core.ddd.event.Event causingEvent) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")), causingEvent);
            this.fact = fact;
        }

        public String getFact() {
            return fact;
        }

        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
