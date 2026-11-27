/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.data.event.store.jdbi;

import java.io.ObjectStreamClass;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** JDBI EventStore \u7684 H2 \u5f3a\u7c7b\u578b\u5951\u7ea6\u6d4b\u8bd5\u3002 */
class JdbiEventStoreTest {

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";

    /** \u8d1f\u8d23\u83b7\u53d6 JDBC \u8fde\u63a5\u5e76\u6267\u884c SQL \u64cd\u4f5c\u7684\u8bbf\u95ee\u5165\u53e3\u3002 */
    private Jdbi jdbi;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>jdbi</code>\u3001\u521d\u59cb\u5316<code>eventStore</code>\u3002 */
    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create("jdbc:h2:mem:jdbi-event-store-test;DB_CLOSE_DELAY=-1", "sa", "");
        eventStore = new JdbiEventStore(jdbi);
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>jdbi.useHandle()</code>\u3001<code>handle.execute()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        jdbi.useHandle(handle -> handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

    /**
     * \u9a8c\u8bc1<code>json</code> \u7b49\u4e8e <code>"\"serialized-id\""</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void reconstructedAggregateIdShouldSerializeAsString() throws Exception {
        TestAggregateRootId orderId = new TestAggregateRootId("serialized-id");
        eventStore.append(ORDER_TYPE, orderId,
                java.util.Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("created")), 0);

        AggregateRootId reconstructed = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        String json = JsonMapper.builder().build().writeValueAsString(reconstructed);
        assertEquals("\"serialized-id\"", json);
    }

    /** 验证<code>events.size()</code> 等于 <code>2</code>，<code>events.get(0).version()</code> 等于 <code>1L</code>，<code>events.get(1).version()</code> 等于 <code>2L</code>。 */
    @Test
    void appendAndReadShouldRoundTripBusinessPayload() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-a");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("created"), new OrderCreatedEvent("renamed")), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).version());
        assertEquals(2L, events.get(1).version());
        assertInstanceOf(OrderCreatedEvent.class, events.get(0).payload());
        assertEquals("created", ((OrderCreatedEvent) events.get(0).payload()).getFact());
        assertEquals("renamed", ((OrderCreatedEvent) events.get(1).payload()).getFact());
        assertEquals(orderId.asString(), events.get(0).aggregateId().asString());
    }

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, orderId)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"Unknown event type: io.ddd4j.events.MissingForContractTest"</code>\u3002 */
    @Test
    void unknownPersistedEventTypeMustFailInsteadOfReturningMap() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-unknown-type");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("created")), 0);
        jdbi.useHandle(handle -> handle.createUpdate("UPDATE " + EventStoreConstants.TABLE_NAME
                        + " SET " + EventStoreConstants.COLUMN_EVENT_TYPE
                        + " = :eventType WHERE " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId")
                .bind("eventType", "io.ddd4j.events.MissingForContractTest")
                .bind("aggregateId", orderId.asString())
                .execute());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> eventStore.read(ORDER_TYPE, orderId));
        assertEquals("Unknown event type: io.ddd4j.events.MissingForContractTest", failure.getMessage());
    }

    /** \u9a8c\u8bc1<code>ObjectStreamClass.lookup(first.getClass()).getSerialVersionUID()</code> \u7b49\u4e8e <code>6652084670470061437L</code>\uff0c<code>second</code> \u7b49\u4e8e <code>first</code>\uff0c<code>unique.size()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void readBackAggregateIdsUseValueEquality() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-id-equality");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("created")), 0);

        AggregateRootId first = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        assertEquals(6652084670470061437L, ObjectStreamClass.lookup(first.getClass()).getSerialVersionUID());
        AggregateRootId second = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        java.util.Set<AggregateRootId> unique = new java.util.HashSet<>();
        unique.add(first);
        unique.add(second);
        assertEquals(1, unique.size());
    }

    /** \u9a8c\u8bc1<code>names.contains("UK_POSITION")</code>\u4e3a\u771f\u3002 */
    @Test
    void positionConstraintUsesSharedName() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-position-schema");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("created")), 0);

        List<String> names = jdbi.withHandle(handle -> handle.createQuery(
                        "select upper(constraint_name) from information_schema.table_constraints "
                                + "where upper(table_name) = :tableName")
                .bind("tableName", EventStoreConstants.TABLE_NAME.toUpperCase())
                .mapTo(String.class)
                .list());
        assertTrue(names.contains("UK_POSITION"), "position unique constraint must be named uk_position: " + names);
    }

    /** 验证被测调用 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.expectedVersion()</code> 等于 <code>0L</code>，<code>conflict.actualVersion()</code> 等于 <code>1L</code>。 */
    @Test
    void appendWithStaleVersionShouldReject() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-b");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("first")), 0);

        AggregateVersionConflictException conflict = assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId,
                        Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("second")), 0));
        assertEquals(0L, conflict.expectedVersion());
        assertEquals(1L, conflict.actualVersion());
    }

    /** 验证<code>slice.size()</code> 等于 <code>2</code>，<code>slice.get(0).version()</code> 等于 <code>2L</code>，<code>slice.get(1).version()</code> 等于 <code>3L</code>。 */
    @Test
    void readVersionRangeShouldReturnInclusiveSlice() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-c");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("v1"), new OrderCreatedEvent("v2"), new OrderCreatedEvent("v3")), 0);

        List<StoredEvent> slice = eventStore.read(ORDER_TYPE, orderId, 2, 3);
        assertEquals(2, slice.size());
        assertEquals(2L, slice.get(0).version());
        assertEquals(3L, slice.get(1).version());
    }

    /** 验证<code>events.size()</code> 等于 <code>2</code>，<code>events.get(0).aggregateId().asString()</code> 等于 <code>first.asString()</code>，<code>events.get(1).aggregateId().asString()</code> 等于 <code>second.asString()</code>。 */
    @Test
    void readAllShouldPreserveGlobalPositionOrderAcrossAggregates() {
        TestAggregateRootId first = new TestAggregateRootId("order-d");
        TestAggregateRootId second = new TestAggregateRootId("order-e");
        eventStore.append(ORDER_TYPE, first,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("d")), 0);
        eventStore.append(ORDER_TYPE, second,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("e")), 0);

        List<StoredEvent> events = eventStore.readAll(1, 10);
        assertEquals(2, events.size());
        assertEquals(first.asString(), events.get(0).aggregateId().asString());
        assertEquals(second.asString(), events.get(1).aggregateId().asString());
        assertTrue(events.get(0).position() < events.get(1).position());
        assertEquals(1L, events.get(0).position());
        assertEquals(2L, events.get(1).position());
    }

    /** 验证<code>events.get(0).correlationId()</code>为空，<code>events.get(1).correlationId()</code> 等于 <code>cause.getEventId()</code>，<code>events.get(1).causationId()</code> 等于 <code>cause.getEventId()</code>。 */
    @Test
    void causalityColumnsShouldRoundTrip() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-f");
        OrderCreatedEvent cause = new OrderCreatedEvent("cause");
        OrderCreatedEvent effect = new OrderCreatedEvent("effect", cause);
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(cause, effect), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertNull(events.get(0).correlationId());
        assertEquals(cause.getEventId(), events.get(1).correlationId());
        assertEquals(cause.getEventId(), events.get(1).causationId());
    }

    /** 验证<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing")).size()</code> 等于 <code>0</code>。 */
    @Test
    void readMissingStreamShouldReturnEmpty() {
        assertEquals(0, eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing")).size());
    }

    /** 验证<code>dataType != null &amp;&amp; dataType.startsWith("CHARACTER")</code>为真。 */
    @Test
    void payloadColumnShouldUseTextType() {
        eventStore.append(ORDER_TYPE, new TestAggregateRootId("order-g"),
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("g")), 0);
        String dataType = jdbi.withHandle(handle -> handle.createQuery(
                        "select data_type from information_schema.columns"
                                + " where upper(table_name) = :tableName"
                                + " and upper(column_name) = :columnName")
                .bind("tableName", EventStoreConstants.TABLE_NAME.toUpperCase())
                .bind("columnName", EventStoreConstants.COLUMN_PAYLOAD.toUpperCase())
                .mapTo(String.class)
                .one());
        // H2 2.2 将 TEXT 声明报告为 CHARACTER VARYING（PG 上为 text，容器轨另验）；
        // 断言语义：payload 落在字符类型族，而非数值/二进制
        assertTrue(dataType != null && dataType.startsWith("CHARACTER"));
    }

    /** \u9a8c\u8bc1<code>event.getAggregateVersion()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void appendMustNotMutateInputAggregateVersion() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-input");
        OrderCreatedEvent event = new OrderCreatedEvent("input");

        assertNull(event.getAggregateVersion());
        eventStore.append(ORDER_TYPE, orderId, Collections.<DomainEvent<?>>singletonList(event), 0);

        assertNull(event.getAggregateVersion());
    }

    /** \u9a8c\u8bc1<code>eventStore.readAll(0, 0)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void readAllLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> eventStore.readAll(0, 0));
    }

    /**
     * \u9a8c\u8bc1<code>actual.toInstant()</code> \u7b49\u4e8e <code>expected.toInstant()</code>\uff0c<code>actual.getZone()</code> \u7b49\u4e8e <code>java.time.ZoneOffset.UTC</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void persistedTimestampMustComeFromEvent() throws Exception {
        TestAggregateRootId orderId = new TestAggregateRootId("order-time");
        OrderCreatedEvent event = new OrderCreatedEvent("time");
        java.time.ZonedDateTime expected = java.time.ZonedDateTime.parse("2024-01-02T03:04:05Z");
        java.lang.reflect.Field field = DomainEvent.class.getDeclaredField("eventTimestamp");
        field.setAccessible(true);
        field.set(event, expected);

        eventStore.append(ORDER_TYPE, orderId, Collections.<DomainEvent<?>>singletonList(event), 0);

        java.time.ZonedDateTime actual = eventStore.read(ORDER_TYPE, orderId).get(0).timestamp();
        assertEquals(expected.toInstant(), actual.toInstant());
        assertEquals(java.time.ZoneOffset.UTC, actual.getZone());
    }

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, orderId)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"Missing persisted event timestamp"</code>\u3002 */
    @Test
    void nullPersistedTimestampMustFailInsteadOfInventingCurrentTime() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-1");
        eventStore.append(ORDER_TYPE, orderId,
                java.util.Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("created")), 0);
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

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    private static final class TestAggregateRootId implements AggregateRootId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");

        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
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

    /**
     * 业务事件样例：无参构造 + JavaBean 属性（payload 序列化约定）。
     */
    public static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        /** 保存 <code>fact</code> 对应的 <code>String</code> 数据。 */
        private String fact;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public OrderCreatedEvent() {
            super();
        }

        /**
         * 初始化 <code>OrderCreatedEvent</code> 使用的 <code>fact</code>。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         */
        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        /**
         * 初始化 <code>OrderCreatedEvent</code> 使用的 <code>fact</code>。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         * @param causingEvent 传入此操作的 <code>io.ddd4j.core.ddd.event.Event</code> 类型 <code>causingEvent</code>
         */
        private OrderCreatedEvent(String fact, io.ddd4j.core.ddd.event.Event causingEvent) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")), causingEvent);
            this.fact = fact;
        }

        /**
         * 读取<code>fact</code> 的当前值。
         *
         * @return 返回表达式 <code>fact</code>
         */
        public String getFact() {
            return fact;
        }

        /**
         * 执行 <code>set Fact</code> 对应的处理。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
