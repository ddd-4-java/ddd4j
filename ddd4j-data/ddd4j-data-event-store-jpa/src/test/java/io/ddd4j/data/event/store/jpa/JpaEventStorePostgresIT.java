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
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.*;
import io.ddd4j.data.event.store.jpa.fixture.TransactionBusinessEntity;
import io.ddd4j.data.event.store.jpa.fixture.TransactionOutboxEntity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL 容器轨：验证 JPA EventStore 的真实 DDL、持久化与读回。
 *
 * <p>Docker 不可用时自动跳过（{@code disabledWithoutDocker = true}）。
 *
 * @since 1.0.x
 */
@Testcontainers(disabledWithoutDocker = true)
class JpaEventStorePostgresIT {

    /** 保存 <code>PG</code> 对应的 <code>PostgreSQLContainer&lt;?&gt;</code> 数据。 */
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    /** 保存 <code>"Order"</code>，供 <code>JpaEventStorePostgresIT</code> 的处理流程复用。 */
    private static final String ORDER_TYPE = "Order";
    /** \u7528\u4e8e\u521b\u5efa JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u7684\u5de5\u5382\u3002 */
    private static EntityManagerFactory entityManagerFactory;
    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private EntityManager entityManager;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;

    /**
     * 初始化测试资源，调用 <code>DriverManager.getConnection</code>、<code>PG.getJdbcUrl</code>、<code>PG.getUsername</code>。
     *
     * @throws Exception 执行数据库操作时失败
     */
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

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u5173\u95ed<code>entityManagerFactory</code>\u3002 */
    @AfterAll
    static void closeEntityManagerFactory() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    /** \u9a8c\u8bc1<code>first.getTransaction().isActive()</code>\u4e3a\u771f\uff0c<code>second.getTransaction().isActive()</code>\u4e3a\u771f\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>versionRead.await(10, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>allocatedBeforeCommit</code>\u4e3a\u5047\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void concurrentAppendsMustSerializeGlobalPositionAllocation() throws Exception {
        TestAggregateRootId seed = new TestAggregateRootId("position-seed");
        eventStore.append("Order", seed, Collections.singletonList(new OrderCreatedEvent("seed")), 0);

        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        EntityTransaction firstTransaction = first.getTransaction();
        CountDownLatch versionRead = new CountDownLatch(1);
        CountDownLatch positionLookup = new CountDownLatch(1);
        Future<?> competing = null;
        try {
            TestAggregateRootId firstId = new TestAggregateRootId("position-first");
            firstTransaction.begin();
            JpaEventStore.participating(first).append("Order", firstId,
                    Collections.singletonList(new OrderCreatedEvent("first")), 0);

            JpaStoredEventRepository secondRepository = new JpaStoredEventRepositoryImpl(second) {
                @Override
                public long findCurrentVersion(String aggregateType, String aggregateId) {
                    long version = super.findCurrentVersion(aggregateType, aggregateId);
                    versionRead.countDown();
                    return version;
                }

                @Override
                public long nextPosition() {
                    positionLookup.countDown();
                    return super.nextPosition();
                }
            };
            TestAggregateRootId secondId = new TestAggregateRootId("position-second");
            competing = executor.submit(() -> {
                EntityTransaction transaction = second.getTransaction();
                transaction.begin();
                try {
                    JpaEventStore.participating(second, secondRepository,
                            new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()))
                            .append("Order", secondId,
                                    Collections.singletonList(new OrderCreatedEvent("second")), 0);
                    transaction.commit();
                } finally {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                }
            });
            assertTrue(versionRead.await(10, TimeUnit.SECONDS), "competing transaction did not reach version lookup");
            boolean allocatedBeforeCommit = positionLookup.await(300, TimeUnit.MILLISECONDS);
            firstTransaction.commit();

            assertFalse(allocatedBeforeCommit, "position lookup passed the uncommitted predecessor");
            competing.get(15, TimeUnit.SECONDS);
            entityManager.clear();
            List<StoredEvent> stored = eventStore.readAll(0, 10);
            assertEquals(3, stored.size());
            assertEquals(1L, stored.get(0).position());
            assertEquals(2L, stored.get(1).position());
            assertEquals(3L, stored.get(2).position());
        } finally {
            if (firstTransaction.isActive()) {
                firstTransaction.rollback();
            }
            if (Objects.nonNull(competing)) {
                competing.cancel(true);
            }
            executor.shutdownNow();
            first.close();
            second.close();
        }
    }

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>entityManager</code>\u3001\u521d\u59cb\u5316<code>eventStore</code>\u3002 */
    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new JpaEventStore(entityManager);
    }

    /**
     * 释放测试资源，调用 <code>entityManager.getTransaction</code>、<code>tx.isActive</code>、<code>tx.rollback</code>。
     *
     * @throws Exception 执行数据库操作时失败
     */
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

    /** \u9a8c\u8bc1<code>tx.isActive()</code>\u4e3a\u771f\uff0c<code>entityManager.contains(business)</code>\u4e3a\u771f\uff0c<code>tx.isActive()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>tx.isActive()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>participating.append("Order", aggregateId, List.of(new OrderCreatedEvent(aggregateId)), 0)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"forced failure after event flush"</code>\uff0c<code>tx.isActive()</code>\u4e3a\u5047\u3002 */
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

    /**
     * \u8c03\u7528 <code>entityManager.getTransaction()</code>\u3001<code>tx.begin()</code>\u3001<code>entityManager.persist()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param businessId \u4f20\u7ed9 <code>entityManager.persist()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param outboxId \u4f20\u7ed9 <code>entityManager.persist()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param aggregateId \u7528\u4e8e\u5b9a\u4f4d\u88ab\u6d4b\u805a\u5408\u7684\u6807\u8bc6
     */
    private void commitControlRows(String businessId, String outboxId, TestAggregateRootId aggregateId) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        entityManager.persist(new TransactionBusinessEntity(businessId, "control"));
        entityManager.persist(new TransactionOutboxEntity(outboxId, "control"));
        JpaEventStore.participating(entityManager).append(ORDER_TYPE, aggregateId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("control")), 0);
        tx.commit();
    }

    /**
     * \u6838\u5bf9<code>observer.createNativeQuery("show transaction_isolation").getSingleResult()</code> \u7b49\u4e8e <code>"read committed"</code>\uff0c<code>businessCount</code> \u7b49\u4e8e <code>expectedBusiness</code>\u3002
     * @param label \u4f20\u7ed9 <code>System.out.println()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param businessId \u4f20\u7ed9 <code>observer.createQuery()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param outboxId \u4f20\u7ed9 <code>observer.createQuery()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param aggregateId \u7528\u4e8e\u5b9a\u4f4d\u88ab\u6d4b\u805a\u5408\u7684\u6807\u8bc6
     * @param expectedBusiness \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>long</code> \u8f93\u5165
     * @param expectedOutbox \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>long</code> \u8f93\u5165
     * @param expectedEvents \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>long</code> \u8f93\u5165
     * @param expectedBusinessValue \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     */
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

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>events.get(0).payload()</code> \u7684\u7c7b\u578b\u4e3a <code>OrderCreatedEvent.class</code>\uff0c<code>events.get(1).aggregateId().asString()</code> \u7b49\u4e8e <code>aggregateId.asString()</code>\u3002 */
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

    /**
     * 验证<code>rs.next()</code>为真，<code>rs.getString("data_type")</code> 等于 <code>"text"</code>。
     *
     * @throws Exception 执行数据库操作时失败
     */
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

    /** \u9a8c\u8bc1<code>eventStore.append("Order", aggregateId, List.of(new OrderCreatedEvent(aggregateId)), 0)</code> \u629b\u51fa <code>io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException.class</code>\uff0c<code>conflict.expectedVersion()</code>\u4e3a\u96f6\uff0c<code>eventStore.read("Order", aggregateId)</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
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

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * <p>测试夹具会调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 实际返回<code>TYPE.asString() + ":" + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }

    /**
     * 业务事件样例：无参构造 + JavaBean 属性（Jackson payload 序列化约定）。
     */
    public static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        /** 保存 <code>fact</code> 对应的 <code>String</code> 数据。 */
        private String fact;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public OrderCreatedEvent() {
            super();
        }

        /**
         * 初始化 <code>OrderCreatedEvent</code> 使用的 <code>fact</code>。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         */
        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        /**
         * 读取<code>fact</code> 的当前值。
         *
         * @return 返回表达式 <code>fact</code>
         */
        public String getFact() {
            return fact;
        }

        /**
         * 执行 <code>set Fact</code> 对应的处理。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
