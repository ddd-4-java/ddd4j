/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.jdbi;

import java.io.ObjectStreamClass;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JDBI EventStore 的 H2 强类型契约测试。
 */
class JdbiEventStoreTest {

    private static final String ORDER_TYPE = "Order";
    private Jdbi jdbi;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create("jdbc:h2:mem:jdbi_typed_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        eventStore = new JdbiEventStore(jdbi);
    }

    @AfterEach
    void tearDown() {
        jdbi.useHandle(handle -> handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

    @Test
    void reconstructedAggregateIdShouldSerializeAsString() throws Exception {
        TestAggregateRootId orderId = new TestAggregateRootId("serialized-id");
        eventStore.append(ORDER_TYPE, orderId,
                java.util.Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent(orderId)), 0);

        AggregateRootId reconstructed = eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId();
        String json = JsonMapper.builder().build().writeValueAsString(reconstructed);
        assertThat(json).isEqualTo("\"serialized-id\"");
    }

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

    @Test
    void missingStreamReturnsEmpty() {
        assertThat(eventStore.read(ORDER_TYPE, new TestAggregateRootId("missing"))).isEmpty();
    }

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

    @Test
    void appendMustNotMutateInputAggregateVersion() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-input");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        org.junit.jupiter.api.Assertions.assertNull(event.getAggregateVersion());
        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0);

        org.junit.jupiter.api.Assertions.assertNull(event.getAggregateVersion());
    }

    @Test
    void readAllLimitMustBePositive() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> eventStore.readAll(0, 0));
    }

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

    final static class TestAggregateRootId implements AggregateRootId {

        private static final long serialVersionUID = 0L;

        private final String value;

        private static final EntityType TYPE = new StringEntityType("Order");

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        @JsonProperty("value")
        public String value() {
            return value;
        }

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

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {
        OrderCreatedEvent() {
            super();
        }

        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }

        OrderCreatedEvent(TestAggregateRootId orderId, DomainEvent<?> causingEvent) {
            super(new EntityIdPath(orderId), causingEvent);
        }
    }
}
