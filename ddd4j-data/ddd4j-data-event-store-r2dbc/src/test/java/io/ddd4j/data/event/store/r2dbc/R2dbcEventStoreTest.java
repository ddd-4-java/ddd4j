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

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";
    /** \u4e3a\u53d7\u6d4b R2DBC \u7ec4\u4ef6\u63d0\u4f9b\u6570\u636e\u5e93\u8fde\u63a5\u7684\u5de5\u5382\u3002 */
    private CloseableConnectionFactory connectionFactory;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6ce8\u518c<code>EntityIdRegistry</code>\u3002 */
    @BeforeEach
    void setUp() {
        connectionFactory = H2ConnectionFactory.inMemory(
                "sync_eventstore_" + System.nanoTime(), "sa", "",
                Map.of(H2ConnectionOption.DB_CLOSE_DELAY, "-1"));
        EntityIdRegistry.register(TestAggregateRootId.TYPE_NAME, TestAggregateRootId::new);
        eventStore = new R2dbcEventStore(connectionFactory);
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6ce8\u9500<code>EntityIdRegistry</code>\u3001\u5173\u95ed<code>connection</code>\u3002 */
    @AfterEach
    void tearDown() {
        EntityIdRegistry.unregister(TestAggregateRootId.TYPE_NAME);
        Connection connection = Mono.from(connectionFactory.create()).block();
        if (connection != null) {
            Mono.from(connection.createStatement("DROP TABLE IF EXISTS DDD4J_EVENT_STORE").execute()).block();
            Mono.from(connection.close()).block();
        }
    }

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>events.get(0).aggregateType()</code> \u7b49\u4e8e <code>ORDER_TYPE</code>\uff0c<code>events.get(0).eventId()</code> \u7b49\u4e8e <code>event.getEventId()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.read("Invoice", orderId)</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\u3002 */
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

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    static final class TestAggregateRootId implements AggregateRootId {
        /** \u7528\u4e8e\u6784\u9020\u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u6216\u5b9e\u4f53\u6807\u8bc6\u7684\u903b\u8f91\u7c7b\u578b\u540d\u3002 */
        private static final String TYPE_NAME = "R2dbcOrder";
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType(TYPE_NAME);
        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
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

    /** \u8868\u793a\u8ba2\u5355\u521b\u5efa\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u9a8c\u8bc1\u4e8b\u4ef6\u5904\u7406\u94fe\u3002 */
    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderCreatedEvent() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param orderId \u6d4b\u8bd5\u4e2d\u8981\u5b9a\u4f4d\u7684\u8ba2\u5355\u6807\u8bc6
         */
        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
