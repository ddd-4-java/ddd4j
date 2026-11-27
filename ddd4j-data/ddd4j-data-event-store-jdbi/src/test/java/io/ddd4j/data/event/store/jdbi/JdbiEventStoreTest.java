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

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";
    /** \u8d1f\u8d23\u83b7\u53d6 JDBC \u8fde\u63a5\u5e76\u6267\u884c SQL \u64cd\u4f5c\u7684\u8bbf\u95ee\u5165\u53e3\u3002 */
    private Jdbi jdbi;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>jdbi</code>\u3001\u521d\u59cb\u5316<code>eventStore</code>\u3002 */
    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create("jdbc:h2:mem:jdbi_typed_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
                java.util.Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent(orderId)), 0);

        AggregateRootId reconstructed = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        String json = JsonMapper.builder().build().writeValueAsString(reconstructed);
        assertThat(json).isEqualTo("\"serialized-id\"");
    }

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>events.get(0).aggregateType()</code> \u7b49\u4e8e <code>ORDER_TYPE</code>\uff0c<code>events.get(0).payload()</code> \u7684\u7c7b\u578b\u4e3a <code>OrderCreatedEvent.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, orderId)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"Unknown event type: io.ddd4j.events.MissingForContractTest"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>ObjectStreamClass.lookup(first.getClass()).getSerialVersionUID()</code> \u7b49\u4e8e <code>6652084670470061437L</code>\uff0c<code>second</code> \u7b49\u4e8e <code>first</code>\uff0c<code>unique.size()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>names.contains("UK_POSITION")</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>conflict.expectedVersion()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>conflict.actualVersion()</code> \u7b49\u4e8e <code>3L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>events.get(0).aggregateId().asString()</code> \u7b49\u4e8e <code>first.asString()</code>\uff0c<code>events.get(1).aggregateId().asString()</code> \u7b49\u4e8e <code>second.asString()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>events.get(0).correlationId()</code>\u4e3a\u7a7a\uff0c<code>events.get(1).causationId()</code> \u7b49\u4e8e <code>cause.getEventId()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing"))</code>\u4e3a\u7a7a\u3002 */
    @Test
    void missingStreamReturnsEmpty() {
        assertThat(eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing"))).isEmpty();
    }

    /** \u9a8c\u8bc1<code>dataType</code> \u4ee5\u2026\u5f00\u5934 <code>"CHARACTER"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>event.getAggregateVersion()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void appendMustNotMutateInputAggregateVersion() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-input");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        org.junit.jupiter.api.Assertions.assertNull(event.getAggregateVersion());
        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0);

        org.junit.jupiter.api.Assertions.assertNull(event.getAggregateVersion());
    }

    /** \u9a8c\u8bc1<code>eventStore.readAll(0, 0)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void readAllLimitMustBePositive() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> eventStore.readAll(0, 0));
    }

    /**
     * \u9a8c\u8bc1<code>actual.toInstant()</code> \u7b49\u4e8e <code>expected.toInstant()</code>\uff0c<code>actual.getZone()</code> \u7b49\u4e8e <code>java.time.ZoneOffset.UTC</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
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

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, orderId)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"Missing persisted event timestamp"</code>\u3002 */
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

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    final static class TestAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
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

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param orderId \u6d4b\u8bd5\u4e2d\u8981\u5b9a\u4f4d\u7684\u8ba2\u5355\u6807\u8bc6
         * @param causingEvent \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u7684 <code>DomainEvent&lt;?&gt;</code> \u503c
         */
        OrderCreatedEvent(TestAggregateRootId orderId, DomainEvent<?> causingEvent) {
            super(new EntityIdPath(orderId), causingEvent);
        }
    }
}
