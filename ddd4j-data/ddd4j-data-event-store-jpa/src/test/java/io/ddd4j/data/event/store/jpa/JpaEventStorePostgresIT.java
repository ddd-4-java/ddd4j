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

import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.event.store.jpa.fixture.TransactionBusinessEntity;
import io.ddd4j.data.event.store.jpa.fixture.TransactionOutboxEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.RollbackException;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PostgreSQL 容器轨：验证 JPA EventStore 的真实 DDL 与 CLOB 读回。 */
@Testcontainers(disabledWithoutDocker = true)
class JpaEventStorePostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static EntityManagerFactory entityManagerFactory;

    @Test
    void positionLookupMustLeaveCallerTransactionsActive() {
        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            long firstCandidate = new JpaStoredEventRepositoryImpl(first).nextPosition();
            long secondCandidate = new JpaStoredEventRepositoryImpl(second).nextPosition();
            org.junit.jupiter.api.Assertions.assertTrue(first.getTransaction().isActive());
            org.junit.jupiter.api.Assertions.assertTrue(second.getTransaction().isActive());
            // 诊断输出不作为原子分配断言；记录两个尚未提交的事务所读候选值。
            System.out.println("POSITION_CANDIDATES=" + firstCandidate + "," + secondCandidate);
        } finally {
            if (first.getTransaction().isActive()) {
                first.getTransaction().rollback();
            }
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            first.close();
            second.close();
        }
    }
    private EntityManager entityManager;
    private EventStore eventStore;

    @BeforeAll
    static void createEntityManagerFactory() {
        Configuration configuration = new Configuration();
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.connection.url", POSTGRES.getJdbcUrl());
        configuration.setProperty("hibernate.connection.username", POSTGRES.getUsername());
        configuration.setProperty("hibernate.connection.password", POSTGRES.getPassword());
        configuration.setProperty("hibernate.connection.isolation",
                String.valueOf(Connection.TRANSACTION_READ_COMMITTED));
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        configuration.addAnnotatedClass(StoredEventEntity.class);
        configuration.addAnnotatedClass(TransactionBusinessEntity.class);
        configuration.addAnnotatedClass(TransactionOutboxEntity.class);
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
        if (entityManager.isOpen()) {
            EntityTransaction transaction = entityManager.getTransaction();
            if (transaction.isActive()) {
                transaction.rollback();
            }
            entityManager.close();
        }
        EntityManager cleanup = entityManagerFactory.createEntityManager();
        EntityTransaction cleanupTx = cleanup.getTransaction();
        try {
            cleanupTx.begin();
            cleanup.createQuery("DELETE FROM TransactionBusinessEntity").executeUpdate();
            cleanup.createQuery("DELETE FROM TransactionOutboxEntity").executeUpdate();
            cleanup.createQuery("DELETE FROM StoredEventEntity").executeUpdate();
            cleanupTx.commit();
        } finally {
            if (cleanupTx.isActive()) {
                cleanupTx.rollback();
            }
            cleanup.close();
        }
    }

    @Test
    void participatingAppendShouldCommitBusinessEventAndOutboxAtomically() {
        TestAggregateRootId aggregateId = new TestAggregateRootId("order-tx-commit");
        List<DomainEvent<?>> events = List.of(new OrderCreatedEvent(aggregateId));
        EntityTransaction tx = entityManager.getTransaction();

        tx.begin();
        TransactionBusinessEntity business = new TransactionBusinessEntity("b-commit", "before");
        entityManager.persist(business);
        entityManager.persist(new TransactionOutboxEntity("o-commit", "pending"));
        JpaEventStore participating = JpaEventStore.participating(entityManager);
        participating.append("Order", aggregateId, events, 0);
        assertTrue(tx.isActive());
        assertTrue(entityManager.contains(business));
        business.setValue("after");

        assertAtomicState("ATOMICITY_COMMIT_BEFORE", "b-commit", "o-commit", aggregateId,
                0L, 0L, 0L, null);
        tx.commit();
        assertFalse(tx.isActive());

        assertAtomicState("ATOMICITY_COMMIT_AFTER", "b-commit", "o-commit", aggregateId,
                1L, 1L, 1L, "after");
        System.out.println("ATOMICITY_COMMIT_STATE=committed");
    }

    @Test
    void participatingAppendShouldRollBackBusinessEventAndOutboxTogether() {
        TestAggregateRootId controlAggregateId = new TestAggregateRootId("order-tx-rollback-control");
        commitControlRows("b-rollback-control", "o-rollback-control", controlAggregateId);
        TestAggregateRootId aggregateId = new TestAggregateRootId("order-tx-rollback");
        EntityTransaction tx = entityManager.getTransaction();

        tx.begin();
        entityManager.persist(new TransactionBusinessEntity("b-rollback", "target"));
        entityManager.persist(new TransactionOutboxEntity("o-rollback", "pending"));
        JpaEventStore.participating(entityManager).append("Order", aggregateId,
                List.of(new OrderCreatedEvent(aggregateId)), 0);
        assertAtomicState("ATOMICITY_ROLLBACK_BEFORE", "b-rollback", "o-rollback", aggregateId,
                0L, 0L, 0L, null);
        tx.rollback();
        assertFalse(tx.isActive());

        assertAtomicState("ATOMICITY_ROLLBACK_AFTER", "b-rollback", "o-rollback", aggregateId,
                0L, 0L, 0L, null);
        assertAtomicState("ATOMICITY_ROLLBACK_CONTROL", "b-rollback-control", "o-rollback-control",
                controlAggregateId, 1L, 1L, 1L, "control");
        System.out.println("ATOMICITY_ROLLBACK_STATE=rolled-back");
    }

    @Test
    void participatingFailureShouldMakeCaughtTransactionUncommittableWithoutPartialWrites() {
        TestAggregateRootId controlAggregateId = new TestAggregateRootId("order-tx-failure-control");
        commitControlRows("b-failure-control", "o-failure-control", controlAggregateId);
        TestAggregateRootId aggregateId = new TestAggregateRootId("order-tx-failure");
        EntityTransaction tx = entityManager.getTransaction();
        JpaStoredEventRepositoryImpl failingRepository = new JpaStoredEventRepositoryImpl(entityManager) {
            @Override
            public void save(StoredEventEntity storedEvent) {
                super.save(storedEvent);
                entityManager.flush();
                throw new IllegalStateException("forced failure after event flush");
            }
        };
        JpaEventStore participating = JpaEventStore.participating(entityManager, failingRepository,
                new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));

        tx.begin();
        entityManager.persist(new TransactionBusinessEntity("b-failure", "target"));
        entityManager.persist(new TransactionOutboxEntity("o-failure", "pending"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> participating.append("Order", aggregateId, List.of(new OrderCreatedEvent(aggregateId)), 0));
        assertEquals("forced failure after event flush", failure.getMessage());
        assertTrue(tx.isActive());
        assertTrue(tx.getRollbackOnly());
        assertAtomicState("ATOMICITY_FAILURE_BEFORE_COMMIT", "b-failure", "o-failure", aggregateId,
                0L, 0L, 0L, null);

        RollbackException rollback = null;
        try {
            tx.commit();
        } catch (RollbackException expected) {
            rollback = expected;
        }
        assertFalse(tx.isActive());
        assertAtomicState("ATOMICITY_FAILURE_AFTER_COMMIT", "b-failure", "o-failure", aggregateId,
                0L, 0L, 0L, null);
        assertAtomicState("ATOMICITY_FAILURE_CONTROL", "b-failure-control", "o-failure-control",
                controlAggregateId, 1L, 1L, 1L, "control");
        System.out.println("ATOMICITY_FAILURE_COMMIT="
                + (Objects.isNull(rollback) ? "rolled-back-without-exception" : rollback.getClass().getSimpleName()));
    }

    private void commitControlRows(String businessId, String outboxId, TestAggregateRootId aggregateId) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        entityManager.persist(new TransactionBusinessEntity(businessId, "control"));
        entityManager.persist(new TransactionOutboxEntity(outboxId, "control"));
        JpaEventStore.participating(entityManager).append("Order", aggregateId,
                List.of(new OrderCreatedEvent(aggregateId)), 0);
        tx.commit();
    }

    private void assertAtomicState(String label, String businessId, String outboxId,
                                   TestAggregateRootId aggregateId, long expectedBusiness,
                                   long expectedOutbox, long expectedEvents, String expectedBusinessValue) {
        EntityManager observer = entityManagerFactory.createEntityManager();
        EntityTransaction observerTx = observer.getTransaction();
        try {
            observerTx.begin();
            assertEquals("read committed", observer.createNativeQuery("show transaction_isolation").getSingleResult());
            long businessCount = observer.createQuery(
                            "select count(e) from TransactionBusinessEntity e where e.id = :id", Long.class)
                    .setParameter("id", businessId)
                    .getSingleResult();
            long outboxCount = observer.createQuery(
                            "select count(e) from TransactionOutboxEntity e where e.id = :id", Long.class)
                    .setParameter("id", outboxId)
                    .getSingleResult();
            long eventCount = observer.createQuery(
                            "select count(e) from StoredEventEntity e where e.aggregateType = :aggregateType"
                                    + " and e.aggregateId = :aggregateId", Long.class)
                    .setParameter("aggregateType", "Order")
                    .setParameter("aggregateId", aggregateId.asString())
                    .getSingleResult();
            assertEquals(expectedBusiness, businessCount);
            assertEquals(expectedOutbox, outboxCount);
            assertEquals(expectedEvents, eventCount);
            TransactionBusinessEntity business = observer.find(TransactionBusinessEntity.class, businessId);
            if (expectedBusiness == 0L) {
                assertNull(business);
            } else {
                assertEquals(expectedBusinessValue, business.getValue());
            }
            System.out.println(label + "=business:" + businessCount + ",event:" + eventCount
                    + ",outbox:" + outboxCount + ",businessValue:"
                    + (Objects.isNull(business) ? "null" : business.getValue()));
            observerTx.commit();
        } finally {
            if (observerTx.isActive()) {
                observerTx.rollback();
            }
            observer.close();
        }
    }

    @Test
    void appendAndReadShouldRoundTripPayloadOnPostgres() {
        TestAggregateRootId aggregateId = new TestAggregateRootId("order-pg-1");
        OrderCreatedEvent event = new OrderCreatedEvent(aggregateId);

        eventStore.append("Order", aggregateId, List.of(event), 0L);

        List<StoredEvent> events = eventStore.read("Order", aggregateId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
    }

    @Test
    void payloadColumnShouldUsePortableTextType() {
        Object dataType = entityManager.createNativeQuery("""
                select data_type
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'ddd4j_event_store'
                  and column_name = 'payload'
                """).getSingleResult();

        assertThat(dataType).isEqualTo("text");
    }

    @Test
    void appendWithStaleVersionShouldRejectOnPostgres() {
        TestAggregateRootId aggregateId = new TestAggregateRootId("order-pg-conflict");
        eventStore.append("Order", aggregateId, List.of(new OrderCreatedEvent(aggregateId)), 0);
        io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException conflict =
                org.junit.jupiter.api.Assertions.assertThrows(
                        io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException.class,
                        () -> eventStore.append("Order", aggregateId,
                                List.of(new OrderCreatedEvent(aggregateId)), 0));
        assertThat(conflict.expectedVersion()).isZero();
        assertThat(conflict.actualVersion()).isEqualTo(1);
        entityManager.clear();
        assertThat(eventStore.read("Order", aggregateId)).hasSize(1);
    }

    private record TestAggregateRootId(String value) implements AggregateRootId {

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

        OrderCreatedEvent(TestAggregateRootId aggregateId) {
            super(new EntityIdPath(aggregateId));
        }
    }
}
