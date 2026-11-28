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

/**
 * 使用真实 Quarkus JTA 事务验证 JPA Managed 参与模式的数据库结果。
 */
@Slf4j
@QuarkusTest
@TestProfile(JpaManagedParticipationIT.JpaManagedTestProfile.class)
class JpaManagedParticipationIT {

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";

    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    @Inject
    EntityManager entityManager;

    /** \u7528\u4e8e\u521b\u5efa JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u7684\u5de5\u5382\u3002 */
    @Inject
    EntityManagerFactory entityManagerFactory;

    /** \u7528\u4e8e\u542f\u52a8\u548c\u56de\u6eda\u6d4b\u8bd5\u4e8b\u52a1\u7684\u4e8b\u52a1\u7ba1\u7406\u5668\u3002 */
    @Inject
    TransactionManager transactionManager;

    /**
     * \u6309 <code>(expectedType.isInstance(current))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
     * @param failure \u89e6\u53d1\u5f53\u524d\u6d4b\u8bd5\u9519\u8bef\u5904\u7406\u8def\u5f84\u7684\u5931\u8d25
     * @param expectedType \u4f20\u7ed9 <code>expectedType.isInstance()</code> \u7684 <code>Class&lt;? extends Throwable&gt;</code> \u8f93\u5165
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>true</code>\u3001<code>false</code>
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

    /** \u9a8c\u8bc1<code>entityManager.contains(business)</code>\u4e3a\u771f\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>runAllowingRollbackCompletion()</code>\u3001<code>persistBusinessAndOutbox()</code>\u3001<code>JpaEventStore.participatingManaged()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>store.append( ORDER_TYPE, new TestAggregateRootId(id), Collections.singletonList(new TestEvent(id)), 1L)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>QuarkusTransaction.isRollbackOnly()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>QuarkusTransaction.isRollbackOnly()</code>\u4e3a\u5047\uff0c<code>propagated.getSuppressed()[0]</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>markerFailure</code>\u3002 */
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
     * \u8c03\u7528 <code>entityManager.persist()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     */
    private void persistBusinessAndOutbox(String id) {
        entityManager.persist(new JpaManagedBusinessEntity(id, "created"));
        entityManager.persist(new JpaManagedOutboxEntity(id, "pending"));
    }

    /** \u8c03\u7528 <code>transactionManager.setRollbackOnly()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
    private void markRollbackOnly() {
        try {
            transactionManager.setRollbackOnly();
        } catch (SystemException exception) {
            throw new IllegalStateException("Failed to mark the current JTA transaction rollback-only", exception);
        }
    }

    /**
     * \u8c03\u7528 <code>QuarkusTransaction.requiringNew()</code>\u3001<code>hasCause()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param action \u9700\u8981\u5728\u5f53\u524d\u6d4b\u8bd5\u9636\u6bb5\u6267\u884c\u7684\u56de\u8c03
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
     * \u6838\u5bf9<code>state.businessValue</code> \u7b49\u4e8e <code>expectedBusinessValue</code>\uff0c<code>state.outboxValue</code> \u7b49\u4e8e <code>"pending"</code>\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @param expectedBusinessValue \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
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
     * \u6838\u5bf9<code>state.businessValue</code>\u4e3a\u7a7a\uff0c<code>state.outboxValue</code>\u4e3a\u7a7a\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
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
     * \u8c03\u7528 <code>QuarkusTransaction.requiringNew()</code>\u3001<code>entityManagerFactory.createEntityManager()</code>\u3001<code>verifier.joinTransaction()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>DatabaseState</code> \u7ed3\u679c
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
         * \u8bfb\u53d6 <code>configuration</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>configuration</code>
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

    /** \u5c06 <code>businessValue</code>\u3001<code>outboxValue</code>\u3001<code>eventCount</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private static final class DatabaseState {

        /** \u7528\u4e8e\u6bd4\u8f83\u9886\u57df\u5bf9\u8c61\u7c7b\u578b\u8f6c\u6362\u53ca\u6620\u5c04\u7684\u4e1a\u52a1\u503c\u3002 */
        private final String businessValue;
        /** \u9a8c\u8bc1\u6295\u9012\u6216\u6301\u4e45\u5316\u65f6\u4f7f\u7528\u7684\u53d1\u4ef6\u7bb1\u4e1a\u52a1\u503c\u3002 */
        private final String outboxValue;
        /** \u6d4b\u8bd5\u89c2\u5bdf\u5230\u7684\u5904\u7406\u4e8b\u4ef6\u6570\u91cf\u3002 */
        private final long eventCount;

        /**
         * \u8bbe\u7f6e <code>businessValue</code>\u3001<code>outboxValue</code>\u3001<code>eventCount</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>DatabaseState</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param businessValue \u7528\u4e8e\u521d\u59cb\u5316 <code>DatabaseState</code> \u7684 <code>String</code> \u503c
         * @param outboxValue \u7528\u4e8e\u521d\u59cb\u5316 <code>DatabaseState</code> \u7684 <code>String</code> \u503c
         * @param eventCount \u7528\u4e8e\u521d\u59cb\u5316 <code>DatabaseState</code> \u7684 <code>long</code> \u503c
         */
        private DatabaseState(String businessValue, String outboxValue, long eventCount) {
            this.businessValue = businessValue;
            this.outboxValue = outboxValue;
            this.eventCount = eventCount;
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    private static final class TestAggregateRootId implements AggregateRootId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType(ORDER_TYPE);
        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
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

/**
 * Quarkus 测试源码内的业务实体；JPA 模块 test fixture 不会传播到依赖方。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity(name = "JpaManagedBusinessEntity")
@Table(name = "tx_test_business")
class JpaManagedBusinessEntity {

    /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
    @Id
    private String id;

    /** \u53d1\u9001\u7ed9\u5f53\u524d\u5939\u5177\u6216\u7528\u4e8e\u65ad\u8a00\u5f80\u8fd4\u7ed3\u679c\u7684\u503c\u3002 */
    @Column(name = "payload_value")
    private String value;

    /**
     * \u8bbe\u7f6e <code>id</code>\u3001<code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>JpaManagedBusinessEntity</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
     * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
     * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
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

    /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
    @Id
    private String id;

    /** \u53d1\u9001\u7ed9\u5f53\u524d\u5939\u5177\u6216\u7528\u4e8e\u65ad\u8a00\u5f80\u8fd4\u7ed3\u679c\u7684\u503c\u3002 */
    @Column(name = "payload_value")
    private String value;

    /**
     * \u8bbe\u7f6e <code>id</code>\u3001<code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>JpaManagedOutboxEntity</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
     * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
     * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
     */
    JpaManagedOutboxEntity(String id, String value) {
        this.id = id;
        this.value = value;
    }
}
