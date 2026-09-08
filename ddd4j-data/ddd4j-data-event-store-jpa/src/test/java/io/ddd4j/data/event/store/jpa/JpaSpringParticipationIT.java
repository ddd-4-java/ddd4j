/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.data.event.store.jpa;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.event.store.jpa.fixture.TransactionBusinessEntity;
import io.ddd4j.data.event.store.jpa.fixture.TransactionOutboxEntity;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 Spring JPA 事务验证 Managed 参与模式的数据库结果。 */
@Slf4j
class JpaSpringParticipationIT {

    private static final String ORDER_TYPE = "Order";
    private static EntityManagerFactory entityManagerFactory;
    private static EntityManager sharedEntityManager;
    private static TransactionTemplate transactionTemplate;

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

    @AfterAll
    static void closeTransactionInfrastructure() {
        if (Objects.nonNull(entityManagerFactory) && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

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

    private static void persistBusinessAndOutbox(String id) {
        sharedEntityManager.persist(new TransactionBusinessEntity(id, "created"));
        sharedEntityManager.persist(new TransactionOutboxEntity(id, "pending"));
    }

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

    private static long eventCount(EntityManager verifier, String id) {
        return verifier.createQuery(
                        "select count(e) from StoredEventEntity e where e.aggregateId = :aggregateId", Long.class)
                .setParameter("aggregateId", id)
                .getSingleResult();
    }

    private static final class TestAggregateRootId implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType(ORDER_TYPE);
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

    private static final class TestEvent extends DomainEvent<TestAggregateRootId> {

        private TestEvent() {
            super();
        }

        private TestEvent(String id) {
            super(new EntityIdPath(new TestAggregateRootId(id)));
        }
    }
}
