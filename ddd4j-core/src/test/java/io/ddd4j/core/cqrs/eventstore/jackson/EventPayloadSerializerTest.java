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
package io.ddd4j.core.cqrs.eventstore.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EventPayloadSerializerTest {

    private final JsonMapper sourceMapper = JsonMapper.builder().findAndAddModules().build();
    private final EventPayloadSerializer serializer = new EventPayloadSerializer(sourceMapper);

    @Test
    void serializeShouldCarryBusinessAndEventMetadata() {
        String json = serializer.serialize(new OrderCreatedEvent("order-created"));
        assertTrue(json.contains("\"fact\":\"order-created\""));
        assertTrue(json.contains("\"event-id\""));
        assertTrue(json.contains("\"event-timestamp\""));
        assertTrue(json.contains("\"entity-id-path\""));
        assertFalse(json.contains("eventId"));
        assertFalse(json.contains("eventTimestamp"));
        assertFalse(json.contains("entityIdPath"));
        assertFalse(json.contains("aggregateVersion"));
    }

    @Test
    void roundTripRestoresEventIdentityTimestampAndSource() {
        OrderCreatedEvent original = new OrderCreatedEvent("order-created");
        ZonedDateTime originalTimestamp = original.getEventTimestamp();

        DomainEvent<?> restored = serializer.deserialize(serializer.serialize(original), OrderCreatedEvent.class);

        assertEquals(original.getEventId(), restored.getEventId());
        assertEquals(originalTimestamp.toInstant(), restored.getEventTimestamp().toInstant());
        assertEquals("order-1", restored.source());
        assertEquals("order-created", ((OrderCreatedEvent) restored).getFact());
    }

    @Test
    void roundTripPreservesEntityIdPath() {
        OrderCreatedEvent original = new OrderCreatedEvent("order-created", "order-2");

        OrderCreatedEvent restored = (OrderCreatedEvent) serializer.deserialize(
                serializer.serialize(original), OrderCreatedEvent.class);

        // TestId 未注册到 EntityIdRegistry，回读使用保留值的 StringEntityId。
        assertEquals("String:order-2", restored.getEntityIdPath().asString());
        assertEquals("order-2", restored.getEntityIdPath().last().asString());
    }

    @Test
    void roundTripPreservesEventType() {
        OrderCreatedEvent original = new OrderCreatedEvent("order-created");

        OrderCreatedEvent restored = (OrderCreatedEvent) serializer.deserialize(
                serializer.serialize(original), OrderCreatedEvent.class);

        assertEquals("OrderCreatedEvent", restored.getEventType().asString());
    }

    @Test
    void deserializeShouldRestoreBusinessProperty() {
        String json = serializer.serialize(new OrderCreatedEvent("renamed"));
        DomainEvent<?> event = serializer.deserialize(json, OrderCreatedEvent.class);
        assertInstanceOf(OrderCreatedEvent.class, event);
        assertEquals("renamed", ((OrderCreatedEvent) event).getFact());
    }

    @Test
    void deserializeShouldIgnoreUnknownProperty() {
        DomainEvent<?> event = serializer.deserialize("{\"fact\":\"ok\",\"junk\":\"ignored\"}", OrderCreatedEvent.class);
        assertEquals("ok", ((OrderCreatedEvent) event).getFact());
    }

    @Test
    void unknownClassMarkerCannotChooseDeserializedType() {
        String payload = serializer.serialize(new OrderCreatedEvent("order-created"));
        String withClassMarker = payload.substring(0, payload.length() - 1)
                + ",\"@class\":\"java.lang.ProcessBuilder\"}";

        DomainEvent<?> restored = serializer.deserialize(withClassMarker, OrderCreatedEvent.class);

        assertInstanceOf(OrderCreatedEvent.class, restored);
        assertEquals("order-created", ((OrderCreatedEvent) restored).getFact());
    }

    @Test
    void deserializeThrowsIllegalStateExceptionOnMalformedJson() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> serializer.deserialize("{\"fact\":", OrderCreatedEvent.class));

        assertEquals("Failed to deserialize event", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void constructorRejectsNullSourceMapper() {
        assertThrows(NullPointerException.class, () -> new EventPayloadSerializer(null));
    }

    @Test
    void serializerLeavesSourceMapperStrict() {
        assertTrue(sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        serializer.deserialize("{\"fact\":\"ok\",\"futureProperty\":\"newer-writer\"}", OrderCreatedEvent.class);
        assertTrue(sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    void causalityMetadataUsesHyphenatedPayloadFields() {
        OrderCreatedEvent cause = new OrderCreatedEvent("cause");
        OrderCreatedEvent effect = new OrderCreatedEvent("effect", cause);
        String json = serializer.serialize(effect);
        assertTrue(json.contains("\"correlation-id\""));
        assertTrue(json.contains("\"causation-id\""));
        assertFalse(json.contains("correlationId"));
        assertFalse(json.contains("causationId"));
    }

    private static final class TestId implements AggregateRootId {
        private static final EntityType TYPE = new StringEntityType("Order");
        private final String value;

        private TestId(String value) {
            this.value = value;
        }

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
    }

    /**
     * 业务事件样例：无参构造 + JavaBean 属性（Jackson 往返约定）。
     */
    public static final class OrderCreatedEvent extends DomainEvent<TestId> {
        private String fact;

        public OrderCreatedEvent() {
            super();
        }

        private OrderCreatedEvent(String fact) {
            this(fact, "order-1");
        }

        private OrderCreatedEvent(String fact, String id) {
            super(new EntityIdPath(new TestId(id)));
            this.fact = fact;
        }

        private OrderCreatedEvent(String fact, Event causingEvent) {
            super(new EntityIdPath(new TestId("order-1")), causingEvent);
            this.fact = fact;
        }

        public String getFact() {
            return fact;
        }

        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
