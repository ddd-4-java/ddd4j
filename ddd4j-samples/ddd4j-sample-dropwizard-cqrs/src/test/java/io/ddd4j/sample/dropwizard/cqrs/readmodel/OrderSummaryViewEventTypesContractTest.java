package io.ddd4j.sample.dropwizard.cqrs.readmodel;

import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.sample.dropwizard.cqrs.repository.EventSourcingOrderRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderSummaryViewEventTypesContractTest {

    @Test
    void eventTypesRetainFixedSizeMutableSnapshotContract() {
        OrderSummaryView view = new OrderSummaryView(
                new EventSourcingOrderRepository(new InMemoryEventStore()));
        List<String> types = (List<String>) view.getEventTypes();

        assertEquals(Arrays.asList("OrderCreatedEvent", "OrderPaidEvent"), types);
        assertEquals("OrderCreatedEvent", types.set(0, "local-only"));
        assertThrows(UnsupportedOperationException.class, () -> types.add("extra"));
        assertEquals(Arrays.asList("OrderCreatedEvent", "OrderPaidEvent"), view.getEventTypes());
    }
}
