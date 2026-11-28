/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.jdbi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ObjectStreamClass;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JDBI EventStore 的 H2 强类型契约测试。
 */
class JdbiEventStoreTest {

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";
    /**
     * 负责获取 JDBC 连接并执行 SQL 操作的访问入口。
     */
    private Jdbi jdbi;
    /**
     * 读取及追加领域事件的事件存储。
     */
    private EventStore eventStore;

    /**
     * 测试前依次初始化<code>jdbi</code>、初始化<code>eventStore</code>。
     */
    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create("jdbc:h2:mem:jdbi_typed_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        eventStore = new JdbiEventStore(jdbi);
    }

    /**
     * 测试结束后执行 <code>jdbi.useHandle()</code>、<code>handle.execute()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        jdbi.useHandle(handle -> handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

    /**
     * 验证<code>json</code> 等于 <code>"\"serialized-id\""</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void reconstructedAggregateIdShouldSerializeAsString() throws Exception {
        TestAggregateRootId orderId = new TestAggregateRootId("serialized-id");
        eventStore.append(ORDER_TYPE, orderId,
                java.util.Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent(orderId)), 0);

        AggregateRootId reconstructed = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        String json = JsonMapper.builder().build().writeValueAsString(reconstructed);
        assertThat(json).isEqualTo("\"serialized-id\"");
    }

    /**
     * 验证<code>events</code> 的大小为 <code>1</code>，<code>events.get(0).aggregateType()</code> 等于 <code>ORDER_TYPE</code>，<code>events.get(0).payload()</code> 的类型为 <code>OrderCreatedEvent.class</code>。
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
    }

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, orderId)</code> 抛出 <code>IllegalStateException.class</code>，<code>failure.getMessage()</code> 等于 <code>"Unknown event type: io.ddd4j.events.MissingForContractTest"</code>。
     */
    @Test
    void unknownPersistedEventTypeMustFailInsteadOfReturningMap() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-unknown-type");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0);
        jdbi.useHandle(handle -> handle.createUpdate("UPDATE " + EventStoreConstants.TABLE_NAME
                        + " SET " + EventStoreConstants.COLUMN_EVENT_TYPE
                        + " = :eventType WHERE " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId")
                .bind("eventType", "io.ddd4j.events.MissingForContractTest")
                .bind("aggregateId", orderId.asString())
                .execute());

        IllegalStateException failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> eventStore.read(ORDER_TYPE, orderId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "Unknown event type: io.ddd4j.events.MissingForContractTest", failure.getMessage());
    }

    /**
     * 验证<code>ObjectStreamClass.lookup(first.getClass()).getSerialVersionUID()</code> 等于 <code>6652084670470061437L</code>，<code>second</code> 等于 <code>first</code>，<code>unique.size()</code> 等于 <code>1</code>。
     */
    @Test
    void readBackAggregateIdsUseValueEquality() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-id-equality");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0);

        AggregateRootId first = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        org.junit.jupiter.api.Assertions.assertEquals(6652084670470061437L, ObjectStreamClass.lookup(first.getClass()).getSerialVersionUID());
        AggregateRootId second = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        org.junit.jupiter.api.Assertions.assertEquals(first, second);
        org.junit.jupiter.api.Assertions.assertEquals(first.hashCode(), second.hashCode());
        java.util.Set<AggregateRootId> unique = new java.util.HashSet<>();
        unique.add(first);
        unique.add(second);
        org.junit.jupiter.api.Assertions.assertEquals(1, unique.size());
    }

    /**
     * 验证<code>names.contains("UK_POSITION")</code>为真。
     */
    @Test
    void positionConstraintUsesSharedName() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-position-schema");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0);

        List<String> names = jdbi.withHandle(handle -> handle.createQuery(
                        "select upper(constraint_name) from information_schema.table_constraints "
                                + "where upper(table_name) = :tableName")
                .bind("tableName", EventStoreConstants.TABLE_NAME.toUpperCase())
                .mapTo(String.class)
                .list());
        org.junit.jupiter.api.Assertions.assertTrue(names.contains("UK_POSITION"),
                "position unique constraint must be named uk_position: " + names);
    }

    /**
     * 验证<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.expectedVersion()</code> 等于 <code>0L</code>，<code>conflict.actualVersion()</code> 等于 <code>3L</code>。
     */
    @Test
    void readWithVersionRangeAndConflictShouldFollowCoreContract() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-2");
        eventStore.append(ORDER_TYPE, orderId,
                List.of(new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0);

        assertThat(eventStore.read(ORDER_TYPE, orderId, 1, 2))
                .extracting(StoredEvent::version)
                .containsExactly(1L, 2L);
        AggregateVersionConflictException conflict = org.junit.jupiter.api.Assertions.assertThrows(
                AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0));
        assertThat(conflict.expectedVersion()).isEqualTo(0L);
        assertThat(conflict.actualVersion()).isEqualTo(3L);
    }

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>events.get(0).aggregateId().asString()</code> 等于 <code>first.asString()</code>，<code>events.get(1).aggregateId().asString()</code> 等于 <code>second.asString()</code>。
     */
    @Test
    void readAllPreservesGlobalPositionAcrossAggregates() {
        TestAggregateRootId first = new TestAggregateRootId("order-position-a");
        TestAggregateRootId second = new TestAggregateRootId("order-position-b");
        eventStore.append(ORDER_TYPE, first, List.of(new OrderCreatedEvent(first)), 0);
        eventStore.append(ORDER_TYPE, second, List.of(new OrderCreatedEvent(second)), 0);

        List<StoredEvent> events = eventStore.readAll(1, 10);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).aggregateId().asString()).isEqualTo(first.asString());
        assertThat(events.get(1).aggregateId().asString()).isEqualTo(second.asString());
        assertThat(events).extracting(StoredEvent::position).containsExactly(1L, 2L);
    }

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>events.get(0).correlationId()</code>为空，<code>events.get(1).causationId()</code> 等于 <code>cause.getEventId()</code>。
     */
    @Test
    void causalityColumnsRoundTrip() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-causal");
        OrderCreatedEvent cause = new OrderCreatedEvent(orderId);
        OrderCreatedEvent effect = new OrderCreatedEvent(orderId, cause);
        eventStore.append(ORDER_TYPE, orderId, List.of(cause, effect), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).correlationId()).isNull();
        assertThat(events.get(1).correlationId()).isEqualTo(cause.getEventId());
        assertThat(events.get(1).causationId()).isEqualTo(cause.getEventId());
    }

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing"))</code>为空。
     */
    @Test
    void missingStreamReturnsEmpty() {
        assertThat(eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing"))).isEmpty();
    }

    /**
     * 验证<code>dataType</code> 以…开头 <code>"CHARACTER"</code>。
     */
    @Test
    void payloadColumnUsesCharacterType() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-payload-type");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0);
        String dataType = jdbi.withHandle(handle -> handle.createQuery(
                        "select data_type from information_schema.columns"
                                + " where upper(table_name) = :tableName"
                                + " and upper(column_name) = :columnName")
                .bind("tableName", EventStoreConstants.TABLE_NAME.toUpperCase())
                .bind("columnName", EventStoreConstants.COLUMN_PAYLOAD.toUpperCase())
                .mapTo(String.class)
                .one());
        assertThat(dataType).startsWith("CHARACTER");
    }

    /**
     * 验证<code>event.getAggregateVersion()</code>为空。
     */
    @Test
    void appendMustNotMutateInputAggregateVersion() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-input");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        org.junit.jupiter.api.Assertions.assertNull(event.getAggregateVersion());
        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0);

        org.junit.jupiter.api.Assertions.assertNull(event.getAggregateVersion());
    }

    /**
     * 验证<code>eventStore.readAll(0, 0)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void readAllLimitMustBePositive() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> eventStore.readAll(0, 0));
    }

    /**
     * 验证<code>actual.toInstant()</code> 等于 <code>expected.toInstant()</code>，<code>actual.getZone()</code> 等于 <code>java.time.ZoneOffset.UTC</code>。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    @Test
    void persistedTimestampMustComeFromEvent() throws Exception {
        TestAggregateRootId orderId = new TestAggregateRootId("order-time");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);
        java.time.ZonedDateTime expected = java.time.ZonedDateTime.parse("2024-01-02T03:04:05Z");
        java.lang.reflect.Field field = DomainEvent.class.getDeclaredField("eventTimestamp");
        field.setAccessible(true);
        field.set(event, expected);

        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0);

        java.time.ZonedDateTime actual = eventStore.read(ORDER_TYPE, orderId).get(0).timestamp();
        assertThat(actual.toInstant()).isEqualTo(expected.toInstant());
        assertThat(actual.getZone()).isEqualTo(java.time.ZoneOffset.UTC);
    }

    /**
     * 验证<code>eventStore.read(ORDER_TYPE, orderId)</code> 抛出 <code>IllegalStateException.class</code>，<code>failure.getMessage()</code> 等于 <code>"Missing persisted event timestamp"</code>。
     */
    @Test
    void nullPersistedTimestampMustFailInsteadOfInventingCurrentTime() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-null-time");
        eventStore.append(ORDER_TYPE, orderId,
                java.util.Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent(orderId)), 0);
        jdbi.useHandle(handle -> {
            handle.execute("ALTER TABLE " + EventStoreConstants.TABLE_NAME + " ALTER COLUMN "
                    + EventStoreConstants.COLUMN_TIMESTAMP + " DROP NOT NULL");
            handle.createUpdate("UPDATE " + EventStoreConstants.TABLE_NAME + " SET "
                            + EventStoreConstants.COLUMN_TIMESTAMP + " = NULL WHERE "
                            + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId")
                    .bind("aggregateId", orderId.asString()).execute();
        });

        IllegalStateException failure = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> eventStore.read(ORDER_TYPE, orderId));
        org.junit.jupiter.api.Assertions.assertEquals("Missing persisted event timestamp", failure.getMessage());
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

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         *
         * @param orderId      测试中要定位的订单标识
         * @param causingEvent 用于初始化 <code>OrderCreatedEvent</code> 的 <code>DomainEvent&lt;?&gt;</code> 值
         */
        OrderCreatedEvent(TestAggregateRootId orderId, DomainEvent<?> causingEvent) {
            super(new EntityIdPath(orderId), causingEvent);
        }
    }
}
