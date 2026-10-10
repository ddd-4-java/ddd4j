/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 强类型 {@link EventStore} 的抽象契约测试。
 */
public abstract class EventStoreContractTest {

    /**
     * 契约测试统一使用的聚合根类型标识。
     */
    protected static final String ORDER_TYPE = "Order";
    /**
     * 第一个订单聚合根标识（order-1）。
     */
    protected static final AggregateRootId ORDER_1 = new TestAggregateRootId("order-1");
    /**
     * 第二个订单聚合根标识（order-2）。
     */
    protected static final AggregateRootId ORDER_2 = new TestAggregateRootId("order-2");

    /**
     * 创建对象。
     * @return 方法执行结果。
     */
    protected abstract EventStore createEventStore();

    @Test
    void appendAndReadShouldPreserveTypedDomainEvents() {
        EventStore store = createEventStore();
        TestEvent first = new TestEvent((TestAggregateRootId) ORDER_1);
        TestEvent second = new TestEvent((TestAggregateRootId) ORDER_1);

        store.append(ORDER_TYPE, ORDER_1, List.of(first, second), 0);

        List<StoredEvent> events = store.read(ORDER_TYPE, ORDER_1);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId()).isEqualTo(ORDER_1);
        assertThat(events.get(0).payload()).isSameAs(first);
        assertThat(events.get(1).payload()).isSameAs(second);
        assertThat(events).extracting(StoredEvent::version).containsExactly(1L, 2L);
    }

    @Test
    void appendWithStaleVersionShouldExposeTypedConflict() {
        EventStore store = createEventStore();
        store.append(ORDER_TYPE, ORDER_1, List.of(new TestEvent((TestAggregateRootId) ORDER_1)), 0);

        assertThatThrownBy(() -> store.append(ORDER_TYPE, ORDER_1, List.of(new TestEvent((TestAggregateRootId) ORDER_1)), 0))
                .isInstanceOf(AggregateVersionConflictException.class)
                .satisfies(error -> {
                    AggregateVersionConflictException conflict = (AggregateVersionConflictException) error;
                    assertThat(conflict.aggregateType()).isEqualTo(ORDER_TYPE);
                    assertThat(conflict.aggregateId()).isEqualTo(ORDER_1.asString());
                    assertThat(conflict.expectedVersion()).isEqualTo(0);
                    assertThat(conflict.actualVersion()).isEqualTo(1);
                });
    }

    @Test
    void readWithVersionRangeShouldReturnInclusiveSlice() {
        EventStore store = createEventStore();
        store.append(ORDER_TYPE, ORDER_1, List.of(new TestEvent((TestAggregateRootId) ORDER_1), new TestEvent((TestAggregateRootId) ORDER_1), new TestEvent((TestAggregateRootId) ORDER_1)), 0);

        List<StoredEvent> events = store.read(ORDER_TYPE, ORDER_1, 1, 2);

        assertThat(events).extracting(StoredEvent::version).containsExactly(1L, 2L);
    }

    @Test
    void readAllShouldPreserveGlobalPositionOrderAcrossAggregates() {
        EventStore store = createEventStore();
        store.append(ORDER_TYPE, ORDER_1, List.of(new TestEvent((TestAggregateRootId) ORDER_1)), 0);
        store.append(ORDER_TYPE, ORDER_2, List.of(new TestEvent((TestAggregateRootId) ORDER_2)), 0);

        List<StoredEvent> events = store.readAll(1, 10);

        assertThat(events).extracting(StoredEvent::position).containsExactly(1L, 2L);
        assertThat(events).extracting(StoredEvent::aggregateId).containsExactly(ORDER_1, ORDER_2);
    }

    /**
     * 契约测试用订单聚合根标识：满足 {@link AggregateRootId} 契约。
     *
     * @param value 聚合根标识值
     */
    protected record TestAggregateRootId(String value) implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType("Order");

        /**
         * 获取值。
         * @return 方法执行结果。
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * asString() 方法。
         * @return 结果字符串。
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * asTypedString() 方法。
         * @return 结果字符串。
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }

    /**
     * 类型说明。
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     * @since 3.0.x
     */
    protected static final class TestEvent extends DomainEvent<TestAggregateRootId> {

        private TestEvent(TestAggregateRootId aggregateId) {
            super(new EntityIdPath(aggregateId));
        }
    }
}
