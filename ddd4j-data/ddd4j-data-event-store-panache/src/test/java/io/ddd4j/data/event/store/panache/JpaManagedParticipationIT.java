/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.data.event.store.panache;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.ddd.event.*;
import io.ddd4j.data.event.store.jpa.JpaEventStore;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实 Quarkus JTA 事务验证 JPA Managed 参与模式的数据库结果。
 */
@Slf4j
@QuarkusTest
@TestProfile(JpaManagedParticipationIT.JpaManagedTestProfile.class)
class JpaManagedParticipationIT {

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";

    /**
     * 执行 JPA 查询和持久化操作的实体管理器。
     */
    @Inject
    EntityManager entityManager;

    /**
     * 用于创建 JPA 实体管理器的工厂。
     */
    @Inject
    EntityManagerFactory entityManagerFactory;

    /**
     * 用于启动和回滚测试事务的事务管理器。
     */
    @Inject
    TransactionManager transactionManager;

    /**
     * 按 <code>(expectedType.isInstance(current))</code> 等实际条件选择当前用例返回的结果。
     *
     * @param failure      触发当前测试错误处理路径的失败
     * @param expectedType 传给 <code>expectedType.isInstance()</code> 的 <code>Class&lt;? extends Throwable&gt;</code> 输入
     * @return 根据测试条件返回的可能值，包括 <code>true</code>、<code>false</code>
     */
    private static boolean hasCause(Throwable failure, Class<? extends Throwable> expectedType) {
        Throwable current = failure;
        while (Objects.nonNull(current)) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            if (current == current.getCause()) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 验证<code>entityManager.contains(business)</code>为真。
     */
    @Test
    void managedParticipationShouldCommitAllRowsAndKeepBusinessEntityManaged() {
        String id = "quarkus-commit";

        QuarkusTransaction.requiringNew().run(() -> {
            JpaManagedBusinessEntity business = new JpaManagedBusinessEntity(id, "created");
            entityManager.persist(business);
            entityManager.persist(new JpaManagedOutboxEntity(id, "pending"));
            JpaEventStore store = JpaEventStore.participatingManaged(entityManager, this::markRollbackOnly);

            store.append(ORDER_TYPE, new TestAggregateRootId(id),
                    Collections.singletonList(new TestEvent(id)), 0L);

            assertTrue(entityManager.contains(business));
            business.setValue("updated-after-event");
        });

        assertCommitted(id, "updated-after-event");
    }

    /**
     * 在测试框架中执行 <code>runAllowingRollbackCompletion()</code>、<code>persistBusinessAndOutbox()</code>、<code>JpaEventStore.participatingManaged()</code>，验证该场景。
     */
    @Test
    void managedParticipationShouldFollowOuterRollback() {
        String id = "quarkus-outer-rollback";

        runAllowingRollbackCompletion(() -> {
            persistBusinessAndOutbox(id);
            JpaEventStore.participatingManaged(entityManager, this::markRollbackOnly)
                    .append(ORDER_TYPE, new TestAggregateRootId(id),
                            Collections.singletonList(new TestEvent(id)), 0L);
            markRollbackOnly();
        });

        assertRolledBack(id);
    }

    /**
     * 验证<code>store.append( ORDER_TYPE, new TestAggregateRootId(id), Collections.singletonList(new TestEvent(id)), 1L)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>QuarkusTransaction.isRollbackOnly()</code>为真。
     */
    @Test
    void caughtOperationFailureShouldStillRollBackOuterTransaction() {
        String id = "quarkus-caught-failure";

        runAllowingRollbackCompletion(() -> {
            persistBusinessAndOutbox(id);
            JpaEventStore store = JpaEventStore.participatingManaged(entityManager, this::markRollbackOnly);
            assertThrows(AggregateVersionConflictException.class, () -> store.append(
                    ORDER_TYPE, new TestAggregateRootId(id),
                    Collections.singletonList(new TestEvent(id)), 1L));
            assertTrue(QuarkusTransaction.isRollbackOnly());
        });

        assertRolledBack(id);
    }

    /**
     * 验证被测调用 抛出 <code>AggregateVersionConflictException.class</code>，<code>QuarkusTransaction.isRollbackOnly()</code>为假，<code>propagated.getSuppressed()[0]</code> 与…为同一实例 <code>markerFailure</code>。
     */
    @Test
    void markerFailureShouldKeepOriginalFailureAndFrameworkShouldAbort() {
        String id = "quarkus-marker-failure";
        IllegalStateException markerFailure = new IllegalStateException("quarkus marker failure");
        AtomicReference<AggregateVersionConflictException> originalFailure = new AtomicReference<>();

        AggregateVersionConflictException propagated = assertThrows(AggregateVersionConflictException.class,
                () -> QuarkusTransaction.requiringNew().run(() -> {
                    persistBusinessAndOutbox(id);
                    JpaEventStore store = JpaEventStore.participatingManaged(entityManager, () -> {
                        throw markerFailure;
                    });
                    store.append(ORDER_TYPE, new TestAggregateRootId(id),
                            Collections.singletonList(new TestEvent(id)), 0L);
                    try {
                        store.append(ORDER_TYPE, new TestAggregateRootId(id),
                                Collections.singletonList(new TestEvent(id)), 0L);
                    } catch (AggregateVersionConflictException failure) {
                        originalFailure.set(failure);
                        assertFalse(QuarkusTransaction.isRollbackOnly());
                        throw failure;
                    }
                }));

        assertSame(originalFailure.get(), propagated);
        assertEquals(1, propagated.getSuppressed().length);
        assertSame(markerFailure, propagated.getSuppressed()[0]);
        assertRolledBack(id);
    }

    /**
     * 调用 <code>entityManager.persist()</code> 执行当前夹具操作。
     *
     * @param id 当前要查找的测试记录标识
     */
    private void persistBusinessAndOutbox(String id) {
        entityManager.persist(new JpaManagedBusinessEntity(id, "created"));
        entityManager.persist(new JpaManagedOutboxEntity(id, "pending"));
    }

    /**
     * 调用 <code>transactionManager.setRollbackOnly()</code> 执行当前夹具操作。
     */
    private void markRollbackOnly() {
        try {
            transactionManager.setRollbackOnly();
        } catch (SystemException exception) {
            throw new IllegalStateException("Failed to mark the current JTA transaction rollback-only", exception);
        }
    }

    /**
     * 调用 <code>QuarkusTransaction.requiringNew()</code>、<code>hasCause()</code> 执行当前夹具操作。
     *
     * @param action 需要在当前测试阶段执行的回调
     */
    private void runAllowingRollbackCompletion(Runnable action) {
        try {
            QuarkusTransaction.requiringNew().run(action);
        } catch (QuarkusTransactionException completionFailure) {
            assertTrue(hasCause(completionFailure, RollbackException.class),
                    "Only a rollback-only completion failure may be tolerated");
        }
    }

    /**
     * 核对<code>state.businessValue</code> 等于 <code>expectedBusinessValue</code>，<code>state.outboxValue</code> 等于 <code>"pending"</code>。
     *
     * @param id                    当前要查找的测试记录标识
     * @param expectedBusinessValue 传给 <code>assertEquals()</code> 的 <code>String</code> 输入
     */
    private void assertCommitted(String id, String expectedBusinessValue) {
        DatabaseState state = readDatabaseState(id);
        assertEquals(expectedBusinessValue, state.businessValue);
        assertEquals("pending", state.outboxValue);
        assertEquals(1L, state.eventCount);
        log.info("QUARKUS_TX_STATE scenario={} businessValue={} outboxValue={} eventCount={}",
                id, state.businessValue, state.outboxValue, state.eventCount);
    }

    /**
     * 核对<code>state.businessValue</code>为空，<code>state.outboxValue</code>为空。
     *
     * @param id 当前要查找的测试记录标识
     */
    private void assertRolledBack(String id) {
        DatabaseState state = readDatabaseState(id);
        assertNull(state.businessValue);
        assertNull(state.outboxValue);
        assertEquals(0L, state.eventCount);
        log.info("QUARKUS_TX_STATE scenario={} businessValue={} outboxValue={} eventCount={}",
                id, state.businessValue, state.outboxValue, state.eventCount);
    }

    /**
     * 调用 <code>QuarkusTransaction.requiringNew()</code>、<code>entityManagerFactory.createEntityManager()</code>、<code>verifier.joinTransaction()</code> 执行当前夹具操作。
     *
     * @param id 当前要查找的测试记录标识
     * @return 提供给后续契约断言的 <code>DatabaseState</code> 结果
     */
    private DatabaseState readDatabaseState(String id) {
        return QuarkusTransaction.requiringNew().call(() -> {
            EntityManager verifier = entityManagerFactory.createEntityManager();
            try {
                verifier.joinTransaction();
                JpaManagedBusinessEntity business = verifier.find(JpaManagedBusinessEntity.class, id);
                JpaManagedOutboxEntity outbox = verifier.find(JpaManagedOutboxEntity.class, id);
                long eventCount = verifier.createQuery(
                                "select count(e) from StoredEventEntity e where e.aggregateId = :aggregateId", Long.class)
                        .setParameter("aggregateId", id)
                        .getSingleResult();
                return new DatabaseState(
                        Objects.isNull(business) ? null : business.getValue(),
                        Objects.isNull(outbox) ? null : outbox.getValue(),
                        eventCount);
            } finally {
                verifier.close();
            }
        });
    }

    /**
     * Test profile keeps Quarkus isolated and explicitly indexes the JPA test dependency.
     */
    public static final class JpaManagedTestProfile implements QuarkusTestProfile {

        /**
         * 读取 <code>configuration</code> 供当前测试使用。
         *
         * @return 当前测试使用的结果，取自 <code>configuration</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> configuration = new LinkedHashMap<>();
            configuration.put("quarkus.http.test-port", "0");
            configuration.put("quarkus.index-dependency.jpa-event-store.group-id", "io.ddd4j");
            configuration.put("quarkus.index-dependency.jpa-event-store.artifact-id",
                    "ddd4j-data-event-store-jpa");
            configuration.put("quarkus.hibernate-orm.packages",
                    "io.ddd4j.data.event.store.jpa,io.ddd4j.data.event.store.panache");
            return configuration;
        }
    }

    /**
     * 将 <code>businessValue</code>、<code>outboxValue</code>、<code>eventCount</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static final class DatabaseState {

        /**
         * 用于比较领域对象类型转换及映射的业务值。
         */
        private final String businessValue;
        /**
         * 验证投递或持久化时使用的发件箱业务值。
         */
        private final String outboxValue;
        /**
         * 测试观察到的处理事件数量。
         */
        private final long eventCount;

        /**
         * 设置 <code>businessValue</code>、<code>outboxValue</code>、<code>eventCount</code>，用于构造 <code>DatabaseState</code> 测试实例。
         *
         * @param businessValue 用于初始化 <code>DatabaseState</code> 的 <code>String</code> 值
         * @param outboxValue   用于初始化 <code>DatabaseState</code> 的 <code>String</code> 值
         * @param eventCount    用于初始化 <code>DatabaseState</code> 的 <code>long</code> 值
         */
        private DatabaseState(String businessValue, String outboxValue, long eventCount) {
            this.businessValue = businessValue;
            this.outboxValue = outboxValue;
            this.eventCount = eventCount;
        }
    }

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    private static final class TestAggregateRootId implements AggregateRootId {

        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType(ORDER_TYPE);
        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>TestAggregateRootId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
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
     * 测试专用领域事件，用于控制事件类型和负载。
     */
    private static final class TestEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         */
        private TestEvent() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         *
         * @param id 当前要查找的测试记录标识
         */
        private TestEvent(String id) {
            super(new EntityIdPath(new TestAggregateRootId(id)));
        }
    }
}

/**
 * Quarkus 测试源码内的业务实体；JPA 模块 test fixture 不会传播到依赖方。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity(name = "JpaManagedBusinessEntity")
@Table(name = "tx_test_business")
class JpaManagedBusinessEntity {

    /**
     * 标识当前测试对象的主键或事件标识。
     */
    @Id
    private String id;

    /**
     * 发送给当前夹具或用于断言往返结果的值。
     */
    @Column(name = "payload_value")
    private String value;

    /**
     * 设置 <code>id</code>、<code>value</code>，用于构造 <code>JpaManagedBusinessEntity</code> 测试实例。
     *
     * @param id    标识当前测试对象的主键或事件标识
     * @param value 当前用例传入的值
     */
    JpaManagedBusinessEntity(String id, String value) {
        this.id = id;
        this.value = value;
    }
}

/**
 * Quarkus 测试源码内的 Outbox 实体；只证明同一数据库资源的 JTA 原子性。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity(name = "JpaManagedOutboxEntity")
@Table(name = "tx_test_outbox")
class JpaManagedOutboxEntity {

    /**
     * 标识当前测试对象的主键或事件标识。
     */
    @Id
    private String id;

    /**
     * 发送给当前夹具或用于断言往返结果的值。
     */
    @Column(name = "payload_value")
    private String value;

    /**
     * 设置 <code>id</code>、<code>value</code>，用于构造 <code>JpaManagedOutboxEntity</code> 测试实例。
     *
     * @param id    标识当前测试对象的主键或事件标识
     * @param value 当前用例传入的值
     */
    JpaManagedOutboxEntity(String id, String value) {
        this.id = id;
        this.value = value;
    }
}
