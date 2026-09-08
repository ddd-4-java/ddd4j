/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.data.event.store.panache;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.event.store.jpa.JpaEventStore;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 Quarkus JTA 事务验证 JPA Managed 参与模式的数据库结果。 */
@Slf4j
@QuarkusTest
@TestProfile(JpaManagedParticipationIT.JpaManagedTestProfile.class)
class JpaManagedParticipationIT {

    private static final String ORDER_TYPE = "Order";

    @Inject
    EntityManager entityManager;

    @Inject
    EntityManagerFactory entityManagerFactory;

    @Inject
    TransactionManager transactionManager;

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

    private void persistBusinessAndOutbox(String id) {
        entityManager.persist(new JpaManagedBusinessEntity(id, "created"));
        entityManager.persist(new JpaManagedOutboxEntity(id, "pending"));
    }

    private void markRollbackOnly() {
        try {
            transactionManager.setRollbackOnly();
        } catch (SystemException exception) {
            throw new IllegalStateException("Failed to mark the current JTA transaction rollback-only", exception);
        }
    }

    private void runAllowingRollbackCompletion(Runnable action) {
        try {
            QuarkusTransaction.requiringNew().run(action);
        } catch (QuarkusTransactionException completionFailure) {
            assertTrue(hasCause(completionFailure, RollbackException.class),
                    "Only a rollback-only completion failure may be tolerated");
        }
    }

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

    private void assertCommitted(String id, String expectedBusinessValue) {
        DatabaseState state = readDatabaseState(id);
        assertEquals(expectedBusinessValue, state.businessValue);
        assertEquals("pending", state.outboxValue);
        assertEquals(1L, state.eventCount);
        log.info("QUARKUS_TX_STATE scenario={} businessValue={} outboxValue={} eventCount={}",
                id, state.businessValue, state.outboxValue, state.eventCount);
    }

    private void assertRolledBack(String id) {
        DatabaseState state = readDatabaseState(id);
        assertNull(state.businessValue);
        assertNull(state.outboxValue);
        assertEquals(0L, state.eventCount);
        log.info("QUARKUS_TX_STATE scenario={} businessValue={} outboxValue={} eventCount={}",
                id, state.businessValue, state.outboxValue, state.eventCount);
    }

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

    /** Test profile keeps Quarkus isolated and explicitly indexes the JPA test dependency. */
    public static final class JpaManagedTestProfile implements QuarkusTestProfile {

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

    private static final class DatabaseState {

        private final String businessValue;
        private final String outboxValue;
        private final long eventCount;

        private DatabaseState(String businessValue, String outboxValue, long eventCount) {
            this.businessValue = businessValue;
            this.outboxValue = outboxValue;
            this.eventCount = eventCount;
        }
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

/** Quarkus 测试源码内的业务实体；JPA 模块 test fixture 不会传播到依赖方。 */
@Getter
@Setter
@NoArgsConstructor
@Entity(name = "JpaManagedBusinessEntity")
@Table(name = "tx_test_business")
class JpaManagedBusinessEntity {

    @Id
    private String id;

    @Column(name = "payload_value")
    private String value;

    JpaManagedBusinessEntity(String id, String value) {
        this.id = id;
        this.value = value;
    }
}

/** Quarkus 测试源码内的 Outbox 实体；只证明同一数据库资源的 JTA 原子性。 */
@Getter
@Setter
@NoArgsConstructor
@Entity(name = "JpaManagedOutboxEntity")
@Table(name = "tx_test_outbox")
class JpaManagedOutboxEntity {

    @Id
    private String id;

    @Column(name = "payload_value")
    private String value;

    JpaManagedOutboxEntity(String id, String value) {
        this.id = id;
        this.value = value;
    }
}
