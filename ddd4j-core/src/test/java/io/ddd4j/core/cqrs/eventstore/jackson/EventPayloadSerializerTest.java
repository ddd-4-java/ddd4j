package io.ddd4j.core.cqrs.eventstore.jackson;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.Event;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPayloadSerializerTest {

    private final EventPayloadSerializer serializer = new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build());

    @Test
    void serializeShouldCarryOnlyBusinessProperty() {
        String json = serializer.serialize(new OrderCreatedEvent("order-created"));
        assertTrue(json.contains("\"fact\":\"order-created\""));
        assertFalse(json.contains("eventId"));
        assertFalse(json.contains("eventTimestamp"));
        assertFalse(json.contains("entityIdPath"));
        assertFalse(json.contains("aggregateVersion"));
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
    void causalityMetadataShouldStayOutOfPayload() {
        OrderCreatedEvent cause = new OrderCreatedEvent("cause");
        OrderCreatedEvent effect = new OrderCreatedEvent("effect", cause);
        String json = serializer.serialize(effect);
        assertFalse(json.contains("correlationId"));
        assertFalse(json.contains("causationId"));
    }

    private static final class TestId implements AggregateRootId {
        private static final EntityType TYPE = new StringEntityType("Order");
        private final String value;
        private TestId(String value) { this.value = value; }
        @Override public EntityType getType() { return TYPE; }
        @Override public String asString() { return value; }
        @Override public String asTypedString() { return TYPE.asString() + ":" + value; }
    }

    /** 业务事件样例：无参构造 + JavaBean 属性（Jackson 往返约定）。 */
    public static final class OrderCreatedEvent extends DomainEvent<TestId> {
        private String fact;

        public OrderCreatedEvent() { super(); }

        private OrderCreatedEvent(String fact) { super(new EntityIdPath(new TestId("order-1"))); this.fact = fact; }

        private OrderCreatedEvent(String fact, Event causingEvent) { super(new EntityIdPath(new TestId("order-1")), causingEvent); this.fact = fact; }

        public String getFact() { return fact; }

        public void setFact(String fact) { this.fact = fact; }
    }
}
