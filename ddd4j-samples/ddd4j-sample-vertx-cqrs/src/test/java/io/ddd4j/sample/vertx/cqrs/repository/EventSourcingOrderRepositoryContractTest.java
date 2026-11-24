package io.ddd4j.sample.vertx.cqrs.repository;

import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.sample.order.domain.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventSourcingOrderRepositoryContractTest {

    @Test
    void storedOrderIdentitySurvivesJsonRoundTrip() {
        InMemoryEventStore events = new InMemoryEventStore();
        EventSourcingOrderRepository repository = new EventSourcingOrderRepository(events);
        Order order = Order.draft("VERTX-IDENTITY-1", "buyer-1", "Alice");
        repository.save(order);

        AggregateRootId storedId = events.readAll(1L, 1).get(0).aggregateId();
        String json = JsonKit.toJson(storedId);
        AggregateRootId restoredId = JsonKit.toObject(json, storedId.getClass());

        assertNotNull(restoredId, "stored order identity must be readable after JSON conversion");
        assertEquals(storedId, restoredId);
        assertEquals(storedId.hashCode(), restoredId.hashCode());
        assertEquals(order.id(), restoredId.asString());
        assertEquals("Order:" + order.id(), restoredId.asTypedString());
        assertEquals(1, events.read("Order", restoredId).size());
    }
}
