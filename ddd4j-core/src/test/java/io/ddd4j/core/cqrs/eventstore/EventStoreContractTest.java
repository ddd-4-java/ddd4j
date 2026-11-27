/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.cqrs.eventstore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
     *
     * @return 方法执行结果。
     */
    protected abstract EventStore createEventStore();

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>events.get(0).aggregateType()</code> 等于 <code>ORDER_TYPE</code>，<code>events.get(1).payload()</code> 指向同一实例 <code>second</code>。
     */
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

    /**
     * 验证<code>store.append(ORDER_TYPE, ORDER_1, Arrays.asList(new TestEvent((TestAggregateRootId) ORDER_1)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.aggregateType()</code> 等于 <code>ORDER_TYPE</code>，<code>conflict.actualVersion()</code> 等于 <code>1</code>。
     */
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

    /**
     * 在测试框架中执行 <code>createEventStore()</code>、<code>store.append()</code>、<code>Arrays.asList()</code>，验证该场景。
     */
    @Test
    void readWithVersionRangeShouldReturnInclusiveSlice() {
        EventStore store = createEventStore();
        store.append(ORDER_TYPE, ORDER_1, Arrays.asList(new TestEvent((TestAggregateRootId) ORDER_1), new TestEvent((TestAggregateRootId) ORDER_1), new TestEvent((TestAggregateRootId) ORDER_1)), 0);

        List<StoredEvent> events = store.read(ORDER_TYPE, ORDER_1, 1, 2);

        assertThat(events).extracting(StoredEvent::version).containsExactly(1L, 2L);
    }

    /**
     * 在测试框架中执行 <code>createEventStore()</code>、<code>store.append()</code>、<code>Arrays.asList()</code>，验证该场景。
     */
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
         * 创建聚合根标识实例。
         *
         * @param value 聚合根标识值
         */
        @JsonCreator()
        protected TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * 获取值。
         *
         * @return 方法执行结果。
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * asString() 方法。
         *
         * @return 结果字符串。
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * asTypedString() 方法。
         *
         * @return 结果字符串。
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
     * 类型说明。
     *
     * @author ddd4j
     * @since 1.0.x
     */
    protected static final class TestEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         *
         * @param aggregateId 用于定位被测聚合的标识
         */
        private TestEvent(TestAggregateRootId aggregateId) {
            super(new EntityIdPath(aggregateId));
        }
    }
}
