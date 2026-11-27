/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.data.event.store.jpa;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.ddd.event.*;
import io.ddd4j.data.event.store.jpa.fixture.TransactionBusinessEntity;
import io.ddd4j.data.event.store.jpa.fixture.TransactionOutboxEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实 Spring JPA 事务验证 Managed 参与模式的数据库结果。
 */
@Slf4j
class JpaSpringParticipationIT {

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";
    /**
     * 用于创建 JPA 实体管理器的工厂。
     */
    private static EntityManagerFactory entityManagerFactory;
    /**
     * 执行数据访问操作的 JPA 实体管理器。
     */
    private static EntityManager sharedEntityManager;
    /**
     * 按测试设置执行事务回调的 Spring 模板。
     */
    private static TransactionTemplate transactionTemplate;

    /**
     * 测试前依次初始化<code>entityManagerFactory</code>、初始化<code>transactionTemplate</code>、初始化<code>sharedEntityManager</code>。
     */
    @BeforeAll
    static void setUpTransactionInfrastructure() {
        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:spring_participation;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .addAnnotatedClass(StoredEventEntity.class)
                .addAnnotatedClass(TransactionBusinessEntity.class)
                .addAnnotatedClass(TransactionOutboxEntity.class);
        entityManagerFactory = configuration.buildSessionFactory();
        JpaTransactionManager transactionManager = new JpaTransactionManager(entityManagerFactory);
        transactionManager.afterPropertiesSet();
        transactionTemplate = new TransactionTemplate(transactionManager);
        sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    }

    /**
     * 测试后依次关闭<code>entityManagerFactory</code>。
     */
    @AfterAll
    static void closeTransactionInfrastructure() {
        if (Objects.nonNull(entityManagerFactory) && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    /**
     * 调用 <code>sharedEntityManager.persist()</code> 执行当前夹具操作。
     *
     * @param id 当前要查找的测试记录标识
     */
    private static void persistBusinessAndOutbox(String id) {
        sharedEntityManager.persist(new TransactionBusinessEntity(id, "created"));
        sharedEntityManager.persist(new TransactionOutboxEntity(id, "pending"));
    }

    /**
     * 核对<code>business.getValue()</code> 等于 <code>expectedBusinessValue</code>，<code>outbox.getValue()</code> 等于 <code>"pending"</code>。
     *
     * @param id                    当前要查找的测试记录标识
     * @param expectedBusinessValue 传给 <code>assertEquals()</code> 的 <code>String</code> 输入
     */
    private static void assertCommitted(String id, String expectedBusinessValue) {
        EntityManager verifier = entityManagerFactory.createEntityManager();
        try {
            TransactionBusinessEntity business = verifier.find(TransactionBusinessEntity.class, id);
            TransactionOutboxEntity outbox = verifier.find(TransactionOutboxEntity.class, id);
            long eventCount = eventCount(verifier, id);
            assertEquals(expectedBusinessValue, business.getValue());
            assertEquals("pending", outbox.getValue());
            assertEquals(1L, eventCount);
            log.info("SPRING_TX_STATE scenario={} businessValue={} outboxValue={} eventCount={}",
                    id, business.getValue(), outbox.getValue(), eventCount);
        } finally {
            verifier.close();
        }
    }

    /**
     * 核对<code>business</code>为空，<code>outbox</code>为空。
     *
     * @param id 当前要查找的测试记录标识
     */
    private static void assertRolledBack(String id) {
        EntityManager verifier = entityManagerFactory.createEntityManager();
        try {
            TransactionBusinessEntity business = verifier.find(TransactionBusinessEntity.class, id);
            TransactionOutboxEntity outbox = verifier.find(TransactionOutboxEntity.class, id);
            long eventCount = eventCount(verifier, id);
            assertNull(business);
            assertNull(outbox);
            assertEquals(0L, eventCount);
            log.info("SPRING_TX_STATE scenario={} business={} outbox={} eventCount={}",
                    id, business, outbox, eventCount);
        } finally {
            verifier.close();
        }
    }

    /**
     * 调用 <code>verifier.createQuery()</code> 执行当前夹具操作。
     *
     * @param verifier 传给 <code>verifier.createQuery()</code> 的 <code>EntityManager</code> 输入
     * @param id       当前要查找的测试记录标识
     * @return 提供给后续契约断言的 <code>long</code> 结果
     */
    private static long eventCount(EntityManager verifier, String id) {
        return verifier.createQuery(
                        "select count(e) from StoredEventEntity e where e.aggregateId = :aggregateId", Long.class)
                .setParameter("aggregateId", id)
                .getSingleResult();
    }

    /**
     * 验证<code>sharedEntityManager.contains(business)</code>为真。
     */
    @Test
    void managedParticipationShouldCommitAllRowsAndKeepBusinessEntityManaged() {
        String id = "spring-commit";

        transactionTemplate.execute(status -> {
            TransactionBusinessEntity business = new TransactionBusinessEntity(id, "created");
            sharedEntityManager.persist(business);
            sharedEntityManager.persist(new TransactionOutboxEntity(id, "pending"));
            JpaEventStore store = JpaEventStore.participatingManaged(sharedEntityManager, status::setRollbackOnly);

            store.append(ORDER_TYPE, new TestAggregateRootId(id),
                    Collections.singletonList(new TestEvent(id)), 0L);

            assertTrue(sharedEntityManager.contains(business));
            business.setValue("updated-after-event");
            return null;
        });

        assertCommitted(id, "updated-after-event");
    }

    /**
     * 在测试框架中执行 <code>transactionTemplate.execute()</code>、<code>persistBusinessAndOutbox()</code>、<code>JpaEventStore.participatingManaged()</code>，验证该场景。
     */
    @Test
    void managedParticipationShouldFollowOuterRollback() {
        String id = "spring-outer-rollback";

        transactionTemplate.execute(status -> {
            persistBusinessAndOutbox(id);
            JpaEventStore.participatingManaged(sharedEntityManager, status::setRollbackOnly)
                    .append(ORDER_TYPE, new TestAggregateRootId(id),
                            Collections.singletonList(new TestEvent(id)), 0L);
            status.setRollbackOnly();
            return null;
        });

        assertRolledBack(id);
    }

    /**
     * 验证<code>store.append( ORDER_TYPE, new TestAggregateRootId(id), Collections.singletonList(new TestEvent(id)), 1L)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>status.isRollbackOnly()</code>为真。
     */
    @Test
    void caughtOperationFailureShouldStillRollBackOuterTransaction() {
        String id = "spring-caught-failure";

        transactionTemplate.execute(status -> {
            persistBusinessAndOutbox(id);
            JpaEventStore store = JpaEventStore.participatingManaged(sharedEntityManager, status::setRollbackOnly);
            assertThrows(AggregateVersionConflictException.class, () -> store.append(
                    ORDER_TYPE, new TestAggregateRootId(id),
                    Collections.singletonList(new TestEvent(id)), 1L));
            assertTrue(status.isRollbackOnly());
            return null;
        });

        assertRolledBack(id);
    }

    /**
     * 验证被测调用 抛出 <code>AggregateVersionConflictException.class</code>，<code>status.isRollbackOnly()</code>为假，<code>propagated.getSuppressed()[0]</code> 与…为同一实例 <code>markerFailure</code>。
     */
    @Test
    void markerFailureShouldKeepOriginalFailureAndFrameworkShouldAbort() {
        String id = "spring-marker-failure";
        IllegalStateException markerFailure = new IllegalStateException("spring marker failure");
        AtomicReference<AggregateVersionConflictException> originalFailure = new AtomicReference<>();

        AggregateVersionConflictException propagated = assertThrows(AggregateVersionConflictException.class,
                () -> transactionTemplate.execute(status -> {
                    persistBusinessAndOutbox(id);
                    JpaEventStore store = JpaEventStore.participatingManaged(sharedEntityManager, () -> {
                        throw markerFailure;
                    });
                    store.append(ORDER_TYPE, new TestAggregateRootId(id),
                            Collections.singletonList(new TestEvent(id)), 0L);
                    try {
                        store.append(ORDER_TYPE, new TestAggregateRootId(id),
                                Collections.singletonList(new TestEvent(id)), 0L);
                        return null;
                    } catch (AggregateVersionConflictException failure) {
                        originalFailure.set(failure);
                        assertFalse(status.isRollbackOnly());
                        throw failure;
                    }
                }));

        assertSame(originalFailure.get(), propagated);
        assertEquals(1, propagated.getSuppressed().length);
        assertSame(markerFailure, propagated.getSuppressed()[0]);
        assertRolledBack(id);
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
