/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.r2dbc;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import io.r2dbc.h2.CloseableConnectionFactory;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.h2.H2ConnectionOption;
import io.r2dbc.spi.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2DBC 同步边界的 H2 强类型契约测试。
 */
class R2dbcEventStoreTest {

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";
    /**
     * 为受测 R2DBC 组件提供数据库连接的工厂。
     */
    private CloseableConnectionFactory connectionFactory;
    /**
     * 读取及追加领域事件的事件存储。
     */
    private EventStore eventStore;

    /**
     * 测试前依次注册<code>EntityIdRegistry</code>。
     */
    @BeforeEach
    void setUp() {
        connectionFactory = H2ConnectionFactory.inMemory(
                "sync_eventstore_" + System.nanoTime(), "sa", "",
                Map.of(H2ConnectionOption.DB_CLOSE_DELAY, "-1"));
        EntityIdRegistry.register(TestAggregateRootId.TYPE_NAME, TestAggregateRootId::new);
        eventStore = new R2dbcEventStore(connectionFactory);
    }

    /**
     * 测试后依次注销<code>EntityIdRegistry</code>、关闭<code>connection</code>。
     */
    @AfterEach
    void tearDown() {
        EntityIdRegistry.unregister(TestAggregateRootId.TYPE_NAME);
        Connection connection = Mono.from(connectionFactory.create()).block();
        if (connection != null) {
            Mono.from(connection.createStatement("DROP TABLE IF EXISTS DDD4J_EVENT_STORE").execute()).block();
            Mono.from(connection.close()).block();
        }
    }

    /**
     * 验证<code>events</code> 的大小为 <code>1</code>，<code>events.get(0).aggregateType()</code> 等于 <code>ORDER_TYPE</code>，<code>events.get(0).eventId()</code> 等于 <code>event.getEventId()</code>。
     */
    @Test
    void appendAndReadShouldPreserveTypedMetadata() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(0).eventId()).isEqualTo(event.getEventId());
    }

    /**
     * 验证<code>eventStore.read("Invoice", orderId)</code> 的大小为 <code>1</code>，<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>。
     */
    @Test
    void versionRangeAndAggregateTypeIsolationShouldFollowCoreContract() {
        TestAggregateRootId orderId = new TestAggregateRootId("shared");
        eventStore.append(ORDER_TYPE, orderId,
                List.of(new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0);
        eventStore.append("Invoice", orderId, List.of(new OrderCreatedEvent(orderId)), 0);

        assertThat(eventStore.read(ORDER_TYPE, orderId, 1, 2))
                .extracting(StoredEvent::version)
                .containsExactly(1L, 2L);
        assertThat(eventStore.read("Invoice", orderId)).hasSize(1);
        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0))
                .isInstanceOf(AggregateVersionConflictException.class);
    }

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    static final class TestAggregateRootId implements AggregateRootId {
        /**
         * 用于构造测试事件流或实体标识的逻辑类型名。
         */
        private static final String TYPE_NAME = "R2dbcOrder";
        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType(TYPE_NAME);
        /**
         * 用于组成测试标识的原始值。
         */
        private final String value;

        /**
         * 以给定状态构造 <code>TestAggregateRootId</code> 测试对象。
         *
         * @param value 用于组成测试标识的原始值
         */
        TestAggregateRootId(String value) {
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
         * @return 实际返回<code>TYPE_NAME + ":" + value</code>，供调用方取得当前值
         */
        @Override
        public String asTypedString() {
            return TYPE_NAME + ":" + value;
        }

        /**
         * 比较两个对象的值是否相等。
         *
         * @param object 用于此操作的 <code>object</code>（<code>Object</code>）
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object object) {
            return object instanceof TestAggregateRootId other && value.equals(other.value);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return value.hashCode();
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
