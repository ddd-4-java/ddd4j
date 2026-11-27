/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.jpa;

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
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 强类型 {@link JpaEventStore} 的 H2 集成测试。
 */
class JpaEventStoreIT {

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
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:eventstore_typed;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.connection.username", "sa");
        configuration.setProperty("hibernate.connection.password", "");
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");
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
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();
        entityManager.createQuery("DELETE FROM StoredEventEntity").executeUpdate();
        transaction.commit();
        entityManager.close();
    }

    /**
     * 验证<code>events</code> 的大小为 <code>1</code>，<code>events.get(0).aggregateType()</code> 等于 <code>ORDER_TYPE</code>，<code>events.get(0).eventId()</code> 等于 <code>event.getEventId()</code>。
     */
    @Test
    void appendAndReadShouldPreserveTypedAggregateAndPayload() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId().asString()).isEqualTo(orderId.asString());
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(0).eventId()).isEqualTo(event.getEventId());
    }

    /**
     * 验证<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>eventStore.read(ORDER_TYPE, orderId)</code> 的大小为 <code>1</code>。
     */
    @Test
    void appendWithStaleVersionShouldRollbackAndExposeTypedConflict() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-2");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0);

        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0))
                .isInstanceOf(AggregateVersionConflictException.class);

        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    /**
     * 在测试框架中执行 <code>eventStore.append()</code>、<code>eventStore.read()</code>，验证该场景。
     */
    @Test
    void readWithVersionRangeShouldReturnInclusiveEvents() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-3");
        eventStore.append(ORDER_TYPE, orderId,
                List.of(new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0);

        assertThat(eventStore.read(ORDER_TYPE, orderId, 1, 2))
                .extracting(StoredEvent::version)
                .containsExactly(1L, 2L);
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
         * 用于组成测试标识的原始值。
         */
        private final String value;

        /**
         * 以给定状态构造 <code>TestAggregateRootId</code> 测试对象。
         *
         * @param value 用于组成测试标识的原始值
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
