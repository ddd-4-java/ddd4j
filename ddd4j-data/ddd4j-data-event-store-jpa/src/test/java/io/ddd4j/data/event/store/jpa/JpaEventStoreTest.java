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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>JpaEventStore</code> 在<code>independentAppend</code>、<code>appendAndRead</code>、<code>reopenedStore</code>、<code>repeatedReads</code>等操作中的行为契约。
 */
class JpaEventStoreTest {

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";

    /**
     * 用于创建 JPA 实体管理器的工厂。
     */
    private static EntityManagerFactory entityManagerFactory;
    /**
     * 执行 JPA 查询和持久化操作的实体管理器。
     */
    private EntityManager entityManager;
    /**
     * 读取及追加领域事件的事件存储。
     */
    private EventStore eventStore;

    /**
     * 测试前依次初始化<code>entityManagerFactory</code>。
     */
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

    /**
     * 测试后依次关闭<code>entityManagerFactory</code>。
     */
    @AfterAll
    static void closeEntityManagerFactory() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    /**
     * 测试前依次初始化<code>entityManager</code>、初始化<code>eventStore</code>。
     */
    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new JpaEventStore(entityManager);
    }

    /**
     * 测试后依次关闭<code>entityManager</code>。
     */
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

    /**
     * 验证<code>saved[0]</code>非空，<code>entityManager.contains(saved[0])</code>为假。
     */
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

    /**
     * 验证<code>events.size()</code> 等于 <code>2</code>，<code>events.get(0).version()</code> 等于 <code>1L</code>，<code>events.get(0).aggregateId().asString()</code> 等于 <code>orderId.asString()</code>。
     */
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

    /**
     * 验证<code>framework.loadClass(eventClassName)</code> 抛出 <code>ClassNotFoundException.class</code>，<code>event.getFact()</code> 等于 <code>"created"</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
     */
    @Test
    void reopenedStoreShouldReadHostEventFromContextClassLoader() throws Exception {
        TestAggregateRootId orderId = new TestAggregateRootId("host-event");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("created")), 0);
        entityManager.close();
        entityManager = entityManagerFactory.createEntityManager();

        String storeClassName = JpaEventStore.class.getName();
        String eventClassName = OrderCreatedEvent.class.getName();
        ClassLoader host = JpaEventStoreTest.class.getClassLoader();
        // 模拟框架加载器看不到宿主应用中的领域事件。
        ClassLoader frameworkParent = new ClassLoader(host) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(eventClassName) || name.equals(storeClassName) || name.startsWith(storeClassName + "$")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        URL storeLocation = JpaEventStore.class.getProtectionDomain().getCodeSource().getLocation();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (URLClassLoader framework = new URLClassLoader(new URL[]{storeLocation}, frameworkParent)) {
            assertThrows(ClassNotFoundException.class, () -> framework.loadClass(eventClassName));
            thread.setContextClassLoader(host);
            Class<?> storeClass = framework.loadClass(storeClassName);
            EventStore reopened = (EventStore) storeClass.getConstructor(EntityManager.class).newInstance(entityManager);
            List<StoredEvent> events = reopened.read(ORDER_TYPE, orderId);
            assertEquals(1, events.size());
            OrderCreatedEvent event = assertInstanceOf(OrderCreatedEvent.class, events.get(0).payload());
            assertEquals("created", event.getFact());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /**
     * 验证<code>second</code> 等于 <code>first</code>，<code>second.hashCode()</code> 等于 <code>first.hashCode()</code>。
     */
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
     * 验证<code>JsonMapper.builder().build().writeValueAsString(reconstructed)</code> 等于 <code>"\"serialized-id\""</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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

    /**
     * 验证被测调用 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.expectedVersion()</code> 等于 <code>0L</code>，<code>conflict.actualVersion()</code> 等于 <code>1L</code>。
     */
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

    /**
     * 验证<code>slice.size()</code> 等于 <code>2</code>，<code>slice.get(0).version()</code> 等于 <code>2L</code>，<code>slice.get(1).version()</code> 等于 <code>3L</code>。
     */
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

    /**
     * 验证<code>events.size() &gt;= 2</code>为真，<code>events.get(events.size() - 2).aggregateId().asString()</code> 等于 <code>first.asString()</code>，<code>events.get(events.size() - 2).position() &lt; events.get(events.size() - 1).position()</code>为真。
     */
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

    /**
     * 验证<code>events.get(0).correlationId()</code>为空，<code>events.get(1).correlationId()</code> 等于 <code>cause.getEventId()</code>，<code>events.get(1).causationId()</code> 等于 <code>cause.getEventId()</code>。
     */
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

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing")).size()</code> 等于 <code>0</code>。
     */
    @Test
    void readMissingStreamShouldReturnEmpty() {
        assertEquals(0, eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing")).size());
    }

    /**
     * 验证<code>event.getAggregateVersion()</code>为空。
     */
    @Test
    void appendMustNotMutateInputAggregateVersion() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-input");
        OrderCreatedEvent event = new OrderCreatedEvent("input");

        assertNull(event.getAggregateVersion());
        eventStore.append(ORDER_TYPE, orderId, Collections.<DomainEvent<?>>singletonList(event), 0);

        assertNull(event.getAggregateVersion());
    }

    /**
     * 验证<code>stored.size()</code> 等于 <code>2</code>，<code>stored.get(0).position() &lt; stored.get(1).position()</code>为真，<code>entityManager.getFlushMode()</code> 等于 <code>jakarta.persistence.FlushModeType.COMMIT</code>。
     */
    @Test
    void batchAppendMustWorkWithCommitFlushMode() {
        entityManager.setFlushMode(jakarta.persistence.FlushModeType.COMMIT);
        TestAggregateRootId orderId = new TestAggregateRootId("commit-flush-batch");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("first"), new OrderCreatedEvent("second")), 0);
        entityManager.clear();
        List<StoredEvent> stored = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(2, stored.size());
        assertTrue(stored.get(0).position() < stored.get(1).position());
        assertEquals(jakarta.persistence.FlushModeType.COMMIT, entityManager.getFlushMode());
    }

    /**
     * 验证被测调用 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.actualVersion()</code> 等于 <code>7L</code>，<code>conflict.expectedVersion()</code> 等于 <code>0L</code>。
     */
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

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("source-stream")).isEmpty()</code>为真，<code>stored.size()</code> 等于 <code>1</code>，<code>((OrderCreatedEvent) stored.get(0).payload()).getFact()</code> 等于 <code>"custom-persistence"</code>。
     */
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

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, orderId).size()</code> 等于 <code>1</code>，<code>customized.read(ORDER_TYPE, orderId).isEmpty()</code>为真，<code>customized.readAll(0, 10).isEmpty()</code>为真。
     */
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

    /**
     * 验证被测调用 抛出 <code>RuntimeException.class</code>，<code>afterFailure.size()</code> 等于 <code>1</code>，<code>recovered.get(1).version()</code> 等于 <code>2L</code>。
     */
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

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>callerTransaction.isActive()</code>为真，<code>eventStore.readAll(0, 10)</code> 抛出 <code>IllegalStateException.class</code>。
     */
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

    /**
     * 验证<code>tx.isActive()</code>为真，<code>participating.read(ORDER_TYPE, orderId).size()</code> 等于 <code>1</code>，<code>eventStore.read(ORDER_TYPE, orderId).isEmpty()</code>为真。
     */
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

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>participating.read(ORDER_TYPE, orderId)</code> 抛出 <code>IllegalStateException.class</code>，<code>participating.readAll(0, 10)</code> 抛出 <code>IllegalStateException.class</code>。
     */
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

    /**
     * 验证<code>participating.append(ORDER_TYPE, new TestAggregateRootId("empty-batch"), Collections.emptyList(), 0)</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void participatingEmptyAppendMustRejectWithoutCallerTransaction() {
        JpaEventStore participating = JpaEventStore.participating(entityManager);

        assertThrows(IllegalStateException.class, () -> participating.append(ORDER_TYPE,
                new TestAggregateRootId("empty-batch"), Collections.emptyList(), 0));
    }

    /**
     * 验证<code>tx.isActive()</code>为真，<code>stored.size()</code> 等于 <code>2</code>，<code>((OrderCreatedEvent) stored.get(1).payload()).getFact()</code> 等于 <code>"second"</code>。
     */
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

    /**
     * 验证被测调用 抛出 <code>RuntimeException.class</code>，<code>tx.isActive()</code>为真，<code>tx.getRollbackOnly()</code>为真。
     */
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

    /**
     * 验证<code>participating.read(ORDER_TYPE, orderId).size()</code> 等于 <code>2</code>，<code>entityManager.getFlushMode()</code> 等于 <code>jakarta.persistence.FlushModeType.COMMIT</code>。
     */
    @Test
    void participatingAppendMustPreserveCommitFlushMode() {
        jakarta.persistence.FlushModeType originalFlushMode = entityManager.getFlushMode();
        entityManager.setFlushMode(jakarta.persistence.FlushModeType.COMMIT);
        JpaEventStore participating = JpaEventStore.participating(entityManager);
        TestAggregateRootId orderId = new TestAggregateRootId("participating-commit-flush");
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            participating.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                    new OrderCreatedEvent("first"), new OrderCreatedEvent("second")), 0);

            assertEquals(2, participating.read(ORDER_TYPE, orderId).size());
            assertEquals(jakarta.persistence.FlushModeType.COMMIT, entityManager.getFlushMode());
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            entityManager.setFlushMode(originalFlushMode);
        }
    }

    /**
     * 验证<code>tx.isActive()</code>为真，<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("participating-source-stream")).isEmpty()</code>为真，<code>stored.get(0).position()</code> 等于 <code>84L</code>。
     */
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

    /**
     * 验证<code>eventStore.readAll(0, 0)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void readAllLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> eventStore.readAll(0, 0));
    }

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, orderId).get(0).timestamp().toInstant()</code> 等于 <code>expected.toInstant()</code>。
     *
     * @throws Exception 反射访问被测成员时失败
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

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    private static final class TestAggregateRootId implements AggregateRootId {

        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Order");

        /**
         * 用于组成测试标识的原始值。
         */
        private final String value;

        /**
         * 以给定状态构造 <code>TestAggregateRootId</code> 测试对象。
         *
         * @param value 用于组成测试标识的原始值
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
     * 业务事件样例：无参构造 + JavaBean 属性（payload 序列化约定）。
     */
    public static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * 本用例写入或读取的事件事实。
         */
        private String fact;

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         */
        public OrderCreatedEvent() {
            super();
        }

        /**
         * 以给定状态构造 <code>OrderCreatedEvent</code> 测试对象。
         *
         * @param fact 本用例写入或读取的事件事实
         */
        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         *
         * @param fact         本用例写入或读取的事件事实
         * @param causingEvent 用于初始化 <code>OrderCreatedEvent</code> 的 <code>io.ddd4j.core.ddd.event.Event</code> 值
         */
        private OrderCreatedEvent(String fact, io.ddd4j.core.ddd.event.Event causingEvent) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")), causingEvent);
            this.fact = fact;
        }

        /**
         * 读取<code>fact</code> 字段。
         *
         * @return <code>fact</code> 字段
         */
        public String getFact() {
            return fact;
        }

        /**
         * 设置<code>fact</code> 字段。
         *
         * @param fact 要写入的值
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
