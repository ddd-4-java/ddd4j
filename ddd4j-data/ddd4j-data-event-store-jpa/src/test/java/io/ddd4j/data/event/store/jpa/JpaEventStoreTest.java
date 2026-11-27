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
import io.ddd4j.core.ddd.event.*;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>JpaEventStore</code> \u5728<code>independentAppend</code>\u3001<code>appendAndRead</code>\u3001<code>reopenedStore</code>\u3001<code>repeatedReads</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class JpaEventStoreTest {

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";

    /** \u7528\u4e8e\u521b\u5efa JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u7684\u5de5\u5382\u3002 */
    private static EntityManagerFactory entityManagerFactory;
    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private EntityManager entityManager;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>entityManagerFactory</code>\u3002 */
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

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u5173\u95ed<code>entityManagerFactory</code>\u3002 */
    @AfterAll
    static void closeEntityManagerFactory() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>entityManager</code>\u3001\u521d\u59cb\u5316<code>eventStore</code>\u3002 */
    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new JpaEventStore(entityManager);
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u5173\u95ed<code>entityManager</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>saved[0]</code>\u975e\u7a7a\uff0c<code>entityManager.contains(saved[0])</code>\u4e3a\u5047\u3002 */
    @Test
    void independentAppendShouldDetachPersistedEntity() {
        StoredEventEntity[] saved = new StoredEventEntity[1];
        JpaStoredEventRepository repository = new JpaStoredEventRepositoryImpl(entityManager) {
            @Override
            public void save(StoredEventEntity entity) {
                saved[0] = entity;
                super.save(entity);
            }
        };
        JpaEventStore independent = new JpaEventStore(entityManager, repository);
        independent.append(ORDER_TYPE, new TestAggregateRootId("independent-context"),
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("created")), 0);

        assertNotNull(saved[0]);
        assertFalse(entityManager.contains(saved[0]));
    }

    /** \u9a8c\u8bc1<code>events.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>events.get(0).version()</code> \u7b49\u4e8e <code>1L</code>\uff0c<code>events.get(0).aggregateId().asString()</code> \u7b49\u4e8e <code>orderId.asString()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>second</code> \u7b49\u4e8e <code>first</code>\uff0c<code>second.hashCode()</code> \u7b49\u4e8e <code>first.hashCode()</code>\u3002 */
    @Test
    void repeatedReadsShouldReconstructEqualAggregateIds() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-value-id");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("created")), 0);

        AggregateRootId first = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        AggregateRootId second = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    /**
     * \u9a8c\u8bc1<code>JsonMapper.builder().build().writeValueAsString(reconstructed)</code> \u7b49\u4e8e <code>"\"serialized-id\""</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void reconstructedAggregateIdShouldSerializeAsString() throws Exception {
        TestAggregateRootId orderId = new TestAggregateRootId("serialized-id");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("created")), 0);

        AggregateRootId reconstructed = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        assertEquals("\"serialized-id\"",
                JsonMapper.builder().build().writeValueAsString(reconstructed));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>conflict.expectedVersion()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>conflict.actualVersion()</code> \u7b49\u4e8e <code>1L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>slice.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>slice.get(0).version()</code> \u7b49\u4e8e <code>2L</code>\uff0c<code>slice.get(1).version()</code> \u7b49\u4e8e <code>3L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>events.size() &gt;= 2</code>\u4e3a\u771f\uff0c<code>events.get(events.size() - 2).aggregateId().asString()</code> \u7b49\u4e8e <code>first.asString()</code>\uff0c<code>events.get(events.size() - 2).position() &lt; events.get(events.size() - 1).position()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>events.get(0).correlationId()</code>\u4e3a\u7a7a\uff0c<code>events.get(1).correlationId()</code> \u7b49\u4e8e <code>cause.getEventId()</code>\uff0c<code>events.get(1).causationId()</code> \u7b49\u4e8e <code>cause.getEventId()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing")).size()</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void readMissingStreamShouldReturnEmpty() {
        assertEquals(0, eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing")).size());
    }

    /** \u9a8c\u8bc1<code>event.getAggregateVersion()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void appendMustNotMutateInputAggregateVersion() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-input");
        OrderCreatedEvent event = new OrderCreatedEvent("input");

        assertNull(event.getAggregateVersion());
        eventStore.append(ORDER_TYPE, orderId, Collections.<DomainEvent<?>>singletonList(event), 0);

        assertNull(event.getAggregateVersion());
    }

    /** \u9a8c\u8bc1<code>stored.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>stored.get(0).position() &lt; stored.get(1).position()</code>\u4e3a\u771f\uff0c<code>entityManager.getFlushMode()</code> \u7b49\u4e8e <code>jakarta.persistence.FlushModeType.COMMIT</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>conflict.actualVersion()</code> \u7b49\u4e8e <code>7L</code>\uff0c<code>conflict.expectedVersion()</code> \u7b49\u4e8e <code>0L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("source-stream")).isEmpty()</code>\u4e3a\u771f\uff0c<code>stored.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>((OrderCreatedEvent) stored.get(0).payload()).getFact()</code> \u7b49\u4e8e <code>"custom-persistence"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, orderId).size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>customized.read(ORDER_TYPE, orderId).isEmpty()</code>\u4e3a\u771f\uff0c<code>customized.readAll(0, 10).isEmpty()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>afterFailure.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>recovered.get(1).version()</code> \u7b49\u4e8e <code>2L</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>callerTransaction.isActive()</code>\u4e3a\u771f\uff0c<code>eventStore.readAll(0, 10)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>tx.isActive()</code>\u4e3a\u771f\uff0c<code>participating.read(ORDER_TYPE, orderId).size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>eventStore.read(ORDER_TYPE, orderId).isEmpty()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>participating.read(ORDER_TYPE, orderId)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>participating.readAll(0, 10)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>participating.append(ORDER_TYPE, new TestAggregateRootId("empty-batch"), Collections.emptyList(), 0)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void participatingEmptyAppendMustRejectWithoutCallerTransaction() {
        JpaEventStore participating = JpaEventStore.participating(entityManager);

        assertThrows(IllegalStateException.class, () -> participating.append(ORDER_TYPE,
                new TestAggregateRootId("empty-batch"), Collections.emptyList(), 0));
    }

    /** \u9a8c\u8bc1<code>tx.isActive()</code>\u4e3a\u771f\uff0c<code>stored.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>((OrderCreatedEvent) stored.get(1).payload()).getFact()</code> \u7b49\u4e8e <code>"second"</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>tx.isActive()</code>\u4e3a\u771f\uff0c<code>tx.getRollbackOnly()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>participating.read(ORDER_TYPE, orderId).size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>entityManager.getFlushMode()</code> \u7b49\u4e8e <code>jakarta.persistence.FlushModeType.COMMIT</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>tx.isActive()</code>\u4e3a\u771f\uff0c<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("participating-source-stream")).isEmpty()</code>\u4e3a\u771f\uff0c<code>stored.get(0).position()</code> \u7b49\u4e8e <code>84L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.readAll(0, 0)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void readAllLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> eventStore.readAll(0, 0));
    }

    /**
     * \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, orderId).get(0).timestamp().toInstant()</code> \u7b49\u4e8e <code>expected.toInstant()</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
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

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    private static final class TestAggregateRootId implements AggregateRootId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");

        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        private TestAggregateRootId(String value) {
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
    }

    /**
     * 业务事件样例：无参构造 + JavaBean 属性（payload 序列化约定）。
     */
    public static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        /** \u672c\u7528\u4f8b\u5199\u5165\u6216\u8bfb\u53d6\u7684\u4e8b\u4ef6\u4e8b\u5b9e\u3002 */
        private String fact;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public OrderCreatedEvent() {
            super();
        }

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param fact \u672c\u7528\u4f8b\u5199\u5165\u6216\u8bfb\u53d6\u7684\u4e8b\u4ef6\u4e8b\u5b9e
         */
        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param fact \u672c\u7528\u4f8b\u5199\u5165\u6216\u8bfb\u53d6\u7684\u4e8b\u4ef6\u4e8b\u5b9e
         * @param causingEvent \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u7684 <code>io.ddd4j.core.ddd.event.Event</code> \u503c
         */
        private OrderCreatedEvent(String fact, io.ddd4j.core.ddd.event.Event causingEvent) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")), causingEvent);
            this.fact = fact;
        }

        /**
         * \u8bfb\u53d6<code>fact</code> \u5b57\u6bb5\u3002
         * @return <code>fact</code> \u5b57\u6bb5
         */
        public String getFact() {
            return fact;
        }

        /**
         * \u8bbe\u7f6e<code>fact</code> \u5b57\u6bb5\u3002
         * @param fact \u8981\u5199\u5165\u7684\u503c
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
