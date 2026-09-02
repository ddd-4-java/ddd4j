package io.ddd4j.mq.event;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQEventSerialization;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MqDomainEventPublisherTest {

    private static final class TestId implements AggregateRootId {
        private final String value;
        private TestId(String value) { this.value = value; }
        @Override public EntityType getType() { return new io.ddd4j.core.ddd.event.StringEntityType("Order"); }
        @Override public String asString() { return value; }
        @Override public String asTypedString() { return getType().asString() + ":" + value; }
    }

    private static final class OrderCreated extends DomainEvent<TestId> {
        private OrderCreated(EntityIdPath path) { super(path); }
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_convertsToCarrierAndForwards() {
        final AtomicReference<MQEvent> published = new AtomicReference<MQEvent>();
        MQEventPublisher publisher = new MQEventPublisher() {
            @Override public <T extends MQEvent> void publish(T event, io.ddd4j.mq.contract.MQDestination destination) {
                published.set(event);
            }
        };
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override public <S, T> T deserialize(S src, Class<T> dist) { return null; }
            @Override public <T> T serialize(Object src) { return (T) "{\"orderId\":42}"; }
        };
        MqDomainEventPublisher domainPublisher = new MqDomainEventPublisher(serialization, publisher);

        domainPublisher.publish(new OrderCreated(new EntityIdPath(new TestId("order-1"))));

        MQEvent carrier = published.get();
        assertEquals(MqDomainEventPublisher.DOMAIN_EVENT_TAG, carrier.getTag());
        assertEquals("OrderCreated", carrier.getTopic());
        assertEquals(OrderCreated.class.getName(), ((DomainEventCarrier) carrier).getDomainEventType());
        assertEquals("{\"orderId\":42}", ((DomainEventCarrier) carrier).getPayload());
    }

    @Test
    void publish_nullEvent_noop() {
        MQEventPublisher publisher = new MQEventPublisher() {
            @Override public <T extends MQEvent> void publish(T event, io.ddd4j.mq.contract.MQDestination destination) {
                throw new AssertionError("must not be called");
            }
        };
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override public <S, T> T deserialize(S src, Class<T> dist) { return null; }
            @Override public <T> T serialize(Object src) { return null; }
        };
        new MqDomainEventPublisher(serialization, publisher).publish((DomainEvent<TestId>) null);
    }
}
