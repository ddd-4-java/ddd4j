/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.panache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Panache EventStore 的 H2 强类型契约测试。
 */
class PanacheEventStoreTest {

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";

    /**
     * 用于创建 JPA 实体管理器的工厂。
     */
    private EntityManagerFactory entityManagerFactory;
    /**
     * 执行 JPA 查询和持久化操作的实体管理器。
     */
    private EntityManager entityManager;
    /**
     * 读取及追加领域事件的事件存储。
     */
    private EventStore eventStore;

    /**
     * 测试执行前执行 <code>System.nanoTime()</code>、<code>configuration.buildSessionFactory()</code>、<code>entityManagerFactory.createEntityManager()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url",
                        "jdbc:h2:mem:panache_typed_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .addAnnotatedClass(PanacheStoredEventEntity.class);
        entityManagerFactory = configuration.buildSessionFactory();
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new PanacheEventStore(entityManager);
    }

    /**
     * 测试结束后执行 <code>entityManager.isOpen()</code>、<code>entityManager.close()</code>、<code>entityManagerFactory.isOpen()</code>、<code>entityManagerFactory.close()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
        }
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    /**
     * 验证<code>events</code> 的大小为 <code>1</code>，<code>events.get(0).aggregateType()</code> 等于 <code>ORDER_TYPE</code>，<code>events.get(0).eventId()</code> 等于 <code>event.getEventId()</code>。
     */
    @Test
    void appendAndReadShouldPreserveTypedEventMetadata() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId()).isInstanceOf(AggregateRootId.class);
        assertThat(events.get(0).aggregateId().asString()).isEqualTo(orderId.asString());
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(0).eventId()).isEqualTo(event.getEventId());
    }

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, orderId).clear()</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>eventStore.read(ORDER_TYPE, orderId, 1, 1).clear()</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>eventStore.readAll(0, 10).clear()</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void readResultsShouldBeUnmodifiableSnapshots() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-immutable");
        eventStore.append(ORDER_TYPE, orderId, java.util.Arrays.asList(new OrderCreatedEvent(orderId)), 0);

        assertThatThrownBy(() -> eventStore.read(ORDER_TYPE, orderId).clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> eventStore.read(ORDER_TYPE, orderId, 1, 1).clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> eventStore.readAll(0, 10).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>。
     */
    @Test
    void readWithVersionRangeAndConflictShouldFollowCoreContract() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-2");
        eventStore.append(ORDER_TYPE, orderId,
                List.of(new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0);

        assertThat(eventStore.read(ORDER_TYPE, orderId, 1, 2))
                .extracting(StoredEvent::version)
                .containsExactly(1L, 2L);
        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0))
                .isInstanceOf(AggregateVersionConflictException.class);
    }

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>events.get(0).position()</code> 小于 <code>events.get(1).position()</code>，<code>eventStore.readAll(events.get(1).position(), 10)</code> 的大小为 <code>1</code>。
     */
    @Test
    void readAllShouldRemainPositionOrderedAcrossAggregateTypes() {
        TestAggregateRootId firstId = new TestAggregateRootId("order-3");
        TestAggregateRootId secondId = new TestAggregateRootId("order-4");
        eventStore.append(ORDER_TYPE, firstId, List.of(new OrderCreatedEvent(firstId)), 0);
        eventStore.append("Invoice", secondId, List.of(new OrderCreatedEvent(secondId)), 0);

        List<StoredEvent> events = eventStore.readAll(0, 10);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).position()).isLessThan(events.get(1).position());
        assertThat(eventStore.readAll(events.get(1).position(), 10)).hasSize(1);
    }

    /**
     * 验证<code>eventStore.append( ORDER_TYPE, orderId, Collections.singletonList(new OrderCreatedEvent(orderId)), 1)</code> 抛出 <code>IllegalStateException.class</code>，<code>committed.get(0).eventId()</code> 等于 <code>original.getEventId()</code>，<code>committed.get(0).correlationId()</code> 等于 <code>callerCorrelation</code>。
     */
    @Test
    void appendRejectionShouldLeaveCallerTransactionAndFlushedChangesIntact() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-caller-transaction");
        OrderCreatedEvent original = new OrderCreatedEvent(orderId);
        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(original), 0);

        EntityTransaction caller = entityManager.getTransaction();
        caller.begin();
        try {
            PanacheStoredEventEntity existing = entityManager.createQuery(
                    "SELECT e FROM PanacheStoredEventEntity e", PanacheStoredEventEntity.class).getSingleResult();
            EventId callerCorrelation = new OrderCreatedEvent(orderId).getEventId();
            existing.correlationId = callerCorrelation.asString();
            entityManager.flush();

            assertThatThrownBy(() -> eventStore.append(
                    ORDER_TYPE, orderId, Collections.singletonList(new OrderCreatedEvent(orderId)), 1))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(caller.isActive()).as("caller transaction survives rejected append").isTrue();
            caller.commit();

            entityManager.clear();
            List<StoredEvent> committed = eventStore.read(ORDER_TYPE, orderId);
            assertThat(committed).extracting(StoredEvent::version).containsExactly(1L);
            assertThat(committed.get(0).eventId()).isEqualTo(original.getEventId());
            assertThat(committed.get(0).correlationId()).isEqualTo(callerCorrelation);
        } finally {
            if (caller.isActive()) {
                caller.rollback();
            }
        }
    }

    /**
     * 验证<code>entityManager.getTransaction().isActive()</code>为假，<code>eventStore.read(ORDER_TYPE, orderId)</code>为空，<code>eventStore.readAll(0, 10)</code>为空。
     */
    @Test
    void failureAfterFlushingFirstEventShouldRollBackEntireBatch() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-failed-batch");
        OrderCreatedEvent first = new OrderCreatedEvent(orderId);
        IllegalArgumentException failure = new IllegalArgumentException("invalid second event");
        DomainEvent<TestAggregateRootId> invalid = new DomainEvent<TestAggregateRootId>(new EntityIdPath(orderId)) {
            @Override
            public EventId getEventId() {
                entityManager.flush();
                throw failure;
            }
        };

        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId, Arrays.asList(first, invalid), 0))
                .isSameAs(failure);
        assertThat(entityManager.getTransaction().isActive()).isFalse();
        assertThat(eventStore.read(ORDER_TYPE, orderId)).isEmpty();
        assertThat(eventStore.readAll(0, 10)).isEmpty();

        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(first), 0);
        assertThat(eventStore.readAll(0, 10)).extracting(StoredEvent::position).containsExactly(1L);
    }

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    final static class TestAggregateRootId implements AggregateRootId {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;
        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Order");
        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>TestAggregateRootId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
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

        /**
         * 读取<code>value</code> 字段。
         *
         * @return <code>value</code> 字段
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TestAggregateRootId other = (TestAggregateRootId) obj;
            return Objects.equals(this.value, other.value);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    /**
     * 表示订单创建的测试领域事件，供断言验证事件处理链。
     */
    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {
        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         */
        OrderCreatedEvent() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         *
         * @param orderId 测试中要定位的订单标识
         */
        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
