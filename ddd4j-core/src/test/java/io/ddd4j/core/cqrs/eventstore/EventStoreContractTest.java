/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.cqrs.eventstore;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>events.get(0).aggregateType()</code> \u7b49\u4e8e <code>ORDER_TYPE</code>\uff0c<code>events.get(1).payload()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>second</code>\u3002 */
    @Test
    void appendAndReadShouldPreserveTypedDomainEvents() {
        EventStore store = createEventStore();
        TestEvent first = new TestEvent((TestAggregateRootId) ORDER_1);
        TestEvent second = new TestEvent((TestAggregateRootId) ORDER_1);

        store.append(ORDER_TYPE, ORDER_1, Arrays.asList(first, second), 0);

        List<StoredEvent> events = store.read(ORDER_TYPE, ORDER_1);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId()).isEqualTo(ORDER_1);
        assertThat(events.get(0).payload()).isSameAs(first);
        assertThat(events.get(1).payload()).isSameAs(second);
        assertThat(events).extracting(StoredEvent::version).containsExactly(1L, 2L);
    }

    /** \u9a8c\u8bc1<code>store.append(ORDER_TYPE, ORDER_1, Arrays.asList(new TestEvent((TestAggregateRootId) ORDER_1)), 0)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>conflict.aggregateType()</code> \u7b49\u4e8e <code>ORDER_TYPE</code>\uff0c<code>conflict.actualVersion()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void appendWithStaleVersionShouldExposeTypedConflict() {
        EventStore store = createEventStore();
        store.append(ORDER_TYPE, ORDER_1, Arrays.asList(new TestEvent((TestAggregateRootId) ORDER_1)), 0);

        assertThatThrownBy(() -> store.append(ORDER_TYPE, ORDER_1, Arrays.asList(new TestEvent((TestAggregateRootId) ORDER_1)), 0))
                .isInstanceOf(AggregateVersionConflictException.class)
                .satisfies(error -> {
                    AggregateVersionConflictException conflict = (AggregateVersionConflictException) error;
                    assertThat(conflict.aggregateType()).isEqualTo(ORDER_TYPE);
                    assertThat(conflict.aggregateId()).isEqualTo(ORDER_1.asString());
                    assertThat(conflict.expectedVersion()).isEqualTo(0);
                    assertThat(conflict.actualVersion()).isEqualTo(1);
                });
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>createEventStore()</code>\u3001<code>store.append()</code>\u3001<code>Arrays.asList()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void readWithVersionRangeShouldReturnInclusiveSlice() {
        EventStore store = createEventStore();
        store.append(ORDER_TYPE, ORDER_1, Arrays.asList(new TestEvent((TestAggregateRootId) ORDER_1), new TestEvent((TestAggregateRootId) ORDER_1), new TestEvent((TestAggregateRootId) ORDER_1)), 0);

        List<StoredEvent> events = store.read(ORDER_TYPE, ORDER_1, 1, 2);

        assertThat(events).extracting(StoredEvent::version).containsExactly(1L, 2L);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>createEventStore()</code>\u3001<code>store.append()</code>\u3001<code>Arrays.asList()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void readAllShouldPreserveGlobalPositionOrderAcrossAggregates() {
        EventStore store = createEventStore();
        store.append(ORDER_TYPE, ORDER_1, Arrays.asList(new TestEvent((TestAggregateRootId) ORDER_1)), 0);
        store.append(ORDER_TYPE, ORDER_2, Arrays.asList(new TestEvent((TestAggregateRootId) ORDER_2)), 0);

        List<StoredEvent> events = store.readAll(1, 10);

        assertThat(events).extracting(StoredEvent::position).containsExactly(1L, 2L);
        assertThat(events).extracting(StoredEvent::aggregateId).containsExactly(ORDER_1, ORDER_2);
    }

    /**
     * 契约测试用订单聚合根标识：满足 {@link AggregateRootId} 契约。
     */
    protected final static class TestAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
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

        /**
         * 创建聚合根标识实例。
         *
         * @param value 聚合根标识值
         */
        @JsonCreator()
        protected TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    /**
     * 类型说明。
     * @author ddd4j
     * @since 1.0.x
     */
    protected static final class TestEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param aggregateId \u7528\u4e8e\u5b9a\u4f4d\u88ab\u6d4b\u805a\u5408\u7684\u6807\u8bc6
         */
        private TestEvent(TestAggregateRootId aggregateId) {
            super(new EntityIdPath(aggregateId));
        }
    }
}
