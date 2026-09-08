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
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.event.store.jpa.fixture.TransactionBusinessEntity;
import io.ddd4j.data.event.store.jpa.fixture.TransactionOutboxEntity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.RollbackException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL 容器轨：验证 JPA EventStore 的真实 DDL、持久化与读回。
 *
 * <p>Docker 不可用时自动跳过（{@code disabledWithoutDocker = true}）。
 *
 * @since 1.0.x
 */
@Testcontainers(disabledWithoutDocker = true)
class JpaEventStorePostgresIT {

    private static final String ORDER_TYPE = "Order";

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

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
    static void createEntityManagerFactory() throws Exception {
        // 用原生 JDBC 创建 DDL（TEXT payload，与 2.0/3.0 统一 schema 对齐）
        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE " + EventStoreConstants.TABLE_NAME + " ("
                    + EventStoreConstants.COLUMN_AGGREGATE_ID + " VARCHAR(255) NOT NULL, "
                    + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " VARCHAR(255) NOT NULL, "
                    + EventStoreConstants.COLUMN_VERSION + " BIGINT NOT NULL, "
                    + EventStoreConstants.COLUMN_POSITION + " BIGINT NOT NULL, "
                    + EventStoreConstants.COLUMN_EVENT_TYPE + " VARCHAR(512) NOT NULL, "
                    + EventStoreConstants.COLUMN_EVENT_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_CORRELATION_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_CAUSATION_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_PAYLOAD + " TEXT NOT NULL, "
                    + EventStoreConstants.COLUMN_TIMESTAMP + " TIMESTAMP NOT NULL, "
                    + "PRIMARY KEY (" + EventStoreConstants.COLUMN_AGGREGATE_TYPE + ", "
                    + EventStoreConstants.COLUMN_AGGREGATE_ID + ", " + EventStoreConstants.COLUMN_VERSION + "), "
                    + "CONSTRAINT uk_" + EventStoreConstants.TABLE_NAME + "_position UNIQUE ("
                    + EventStoreConstants.COLUMN_POSITION + ")"
                    + ")");
            stmt.execute("CREATE TABLE tx_test_business (id VARCHAR(255) PRIMARY KEY, payload_value VARCHAR(255))");
            stmt.execute("CREATE TABLE tx_test_outbox (id VARCHAR(255) PRIMARY KEY, payload_value VARCHAR(255))");
        }
        Configuration cfg = new Configuration();
        cfg.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        cfg.setProperty("hibernate.connection.url", PG.getJdbcUrl());
        cfg.setProperty("hibernate.connection.username", PG.getUsername());
        cfg.setProperty("hibernate.connection.password", PG.getPassword());
        cfg.setProperty("hibernate.connection.isolation", String.valueOf(Connection.TRANSACTION_READ_COMMITTED));
        cfg.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQL10Dialect");
        cfg.setProperty("hibernate.show_sql", "false");
        cfg.addAnnotatedClass(StoredEventEntity.class);
        cfg.addAnnotatedClass(TransactionBusinessEntity.class);
        cfg.addAnnotatedClass(TransactionOutboxEntity.class);
        entityManagerFactory = cfg.buildSessionFactory();
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
    void tearDown() throws Exception {
        EntityTransaction tx = entityManager.getTransaction();
        if (tx.isActive()) {
            tx.rollback();
        }
        entityManager.close();
        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE tx_test_business, tx_test_outbox, " + EventStoreConstants.TABLE_NAME);
        }
    }

    @Test
    void participatingAppendShouldCommitBusinessEventAndOutboxAtomically() {
        TestAggregateRootId aggregateId = new TestAggregateRootId("order-tx-commit");
        List<DomainEvent<?>> events = Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("committed"));
        EntityTransaction tx = entityManager.getTransaction();

        tx.begin();
        TransactionBusinessEntity business = new TransactionBusinessEntity("b-commit", "before");
        entityManager.persist(business);
        entityManager.persist(new TransactionOutboxEntity("o-commit", "pending"));
        JpaEventStore participating = JpaEventStore.participating(entityManager);
        participating.append(ORDER_TYPE, aggregateId, events, 0);
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
        JpaEventStore.participating(entityManager).append(ORDER_TYPE, aggregateId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("rolled-back")), 0);
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
                () -> participating.append(ORDER_TYPE, aggregateId,
                        Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("failed")), 0));
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
        JpaEventStore.participating(entityManager).append(ORDER_TYPE, aggregateId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("control")), 0);
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
                    .setParameter("aggregateType", ORDER_TYPE)
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
    void appendAndReadShouldRoundTripTypedEventOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-1");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("created"), new OrderCreatedEvent("renamed")), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).version());
        assertEquals(2L, events.get(1).version());
        assertInstanceOf(OrderCreatedEvent.class, events.get(0).payload());
        assertEquals("created", ((OrderCreatedEvent) events.get(0).payload()).getFact());
        assertEquals(orderId.asString(), events.get(0).aggregateId().asString());
    }

    @Test
    void appendWithStaleVersionShouldRejectOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-2");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("first")), 0);

        AggregateVersionConflictException conflict = assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId,
                        Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("stale")), 0));
        assertEquals(0L, conflict.expectedVersion());
        assertEquals(1L, conflict.actualVersion());
        entityManager.clear();
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
    }

    @Test
    void payloadColumnShouldUsePortableTextType() throws Exception {
        // PG 将列名折叠为小写；information_schema 中 table_schema = current_schema()
        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select data_type from information_schema.columns"
                             + " where lower(table_name) = lower('" + EventStoreConstants.TABLE_NAME + "')"
                             + " and lower(column_name) = lower('" + EventStoreConstants.COLUMN_PAYLOAD + "')")) {
            assertTrue(rs.next(), "payload column must exist");
            assertEquals("text", rs.getString("data_type"));
        }
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

    /** 业务事件样例：无参构造 + JavaBean 属性（Jackson payload 序列化约定）。 */
    public static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        private String fact;

        public OrderCreatedEvent() {
            super();
        }

        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
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
