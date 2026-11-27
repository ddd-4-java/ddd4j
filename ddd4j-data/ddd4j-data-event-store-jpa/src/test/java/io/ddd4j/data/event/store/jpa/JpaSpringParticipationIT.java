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

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";
    /** \u7528\u4e8e\u521b\u5efa JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u7684\u5de5\u5382\u3002 */
    private static EntityManagerFactory entityManagerFactory;
    /** \u6267\u884c\u6570\u636e\u8bbf\u95ee\u64cd\u4f5c\u7684 JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private static EntityManager sharedEntityManager;
    /** \u6309\u6d4b\u8bd5\u8bbe\u7f6e\u6267\u884c\u4e8b\u52a1\u56de\u8c03\u7684 Spring \u6a21\u677f\u3002 */
    private static TransactionTemplate transactionTemplate;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>entityManagerFactory</code>\u3001\u521d\u59cb\u5316<code>transactionTemplate</code>\u3001\u521d\u59cb\u5316<code>sharedEntityManager</code>\u3002 */
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

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u5173\u95ed<code>entityManagerFactory</code>\u3002 */
    @AfterAll
    static void closeTransactionInfrastructure() {
        if (Objects.nonNull(entityManagerFactory) && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    /**
     * \u8c03\u7528 <code>sharedEntityManager.persist()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     */
    private static void persistBusinessAndOutbox(String id) {
        sharedEntityManager.persist(new TransactionBusinessEntity(id, "created"));
        sharedEntityManager.persist(new TransactionOutboxEntity(id, "pending"));
    }

    /**
     * \u6838\u5bf9<code>business.getValue()</code> \u7b49\u4e8e <code>expectedBusinessValue</code>\uff0c<code>outbox.getValue()</code> \u7b49\u4e8e <code>"pending"</code>\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @param expectedBusinessValue \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
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
     * \u6838\u5bf9<code>business</code>\u4e3a\u7a7a\uff0c<code>outbox</code>\u4e3a\u7a7a\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
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
     * \u8c03\u7528 <code>verifier.createQuery()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param verifier \u4f20\u7ed9 <code>verifier.createQuery()</code> \u7684 <code>EntityManager</code> \u8f93\u5165
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>long</code> \u7ed3\u679c
     */
    private static long eventCount(EntityManager verifier, String id) {
        return verifier.createQuery(
                        "select count(e) from StoredEventEntity e where e.aggregateId = :aggregateId", Long.class)
                .setParameter("aggregateId", id)
                .getSingleResult();
    }

    /** \u9a8c\u8bc1<code>sharedEntityManager.contains(business)</code>\u4e3a\u771f\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>transactionTemplate.execute()</code>\u3001<code>persistBusinessAndOutbox()</code>\u3001<code>JpaEventStore.participatingManaged()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>store.append( ORDER_TYPE, new TestAggregateRootId(id), Collections.singletonList(new TestEvent(id)), 1L)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>status.isRollbackOnly()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>status.isRollbackOnly()</code>\u4e3a\u5047\uff0c<code>propagated.getSuppressed()[0]</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>markerFailure</code>\u3002 */
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

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    private static final class TestAggregateRootId implements AggregateRootId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType(ORDER_TYPE);
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

    /** \u6d4b\u8bd5\u4e13\u7528\u9886\u57df\u4e8b\u4ef6\uff0c\u7528\u4e8e\u63a7\u5236\u4e8b\u4ef6\u7c7b\u578b\u548c\u8d1f\u8f7d\u3002 */
    private static final class TestEvent extends DomainEvent<TestAggregateRootId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        private TestEvent() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         */
        private TestEvent(String id) {
            super(new EntityIdPath(new TestAggregateRootId(id)));
        }
    }
}
