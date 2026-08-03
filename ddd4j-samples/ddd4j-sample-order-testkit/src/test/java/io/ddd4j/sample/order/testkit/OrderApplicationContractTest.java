package io.ddd4j.sample.order.testkit;

import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderTransactionPort;
import io.ddd4j.sample.order.application.OutboxDispatchResult;
import io.ddd4j.sample.order.application.OutboxPublisher;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderStatus;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OrderApplicationContractTest {

    @Test
    void shouldExecuteRichModelAndCqrsProjection() {
        OrderScenario scenario = new OrderScenario();
        Order order = scenario.paidOrder("ORDER-001");

        assertEquals(OrderStatus.PAID, order.status());
        assertEquals("119.80", scenario.application().find(order.id()).totalAmount().toPlainString());
        assertEquals(3, scenario.adapters().pending(10).size());
    }

    @Test
    void shouldPublishOutboxAndMarkMessages() {
        OrderScenario scenario = new OrderScenario();
        scenario.paidOrder("ORDER-002");
        AtomicInteger published = new AtomicInteger();
        OutboxPublisher publisher = new OutboxPublisher(scenario.adapters(), message -> published.incrementAndGet());

        assertEquals(3, publisher.publishPending(10));
        assertEquals(3, published.get());
        assertFalse(scenario.adapters().pending(10).iterator().hasNext());
    }

    @Test
    void shouldKeepFailedOutboxMessageForRetry() {
        OrderScenario scenario = new OrderScenario();
        scenario.application().create(new CreateOrderCommand("ORDER-RETRY", "buyer-retry", "Morgan"));
        OutboxPublisher publisher = new OutboxPublisher(scenario.adapters(), message -> {
            throw new IllegalStateException("Kafka is unavailable");
        });

        OutboxDispatchResult result = publisher.dispatchPending(10);

        assertEquals(1, result.attempted());
        assertEquals(0, result.published());
        assertEquals(1, result.failed());
        assertEquals(1, scenario.adapters().pending(10).size());
    }

    @Test
    void shouldMakePaymentIdempotent() {
        OrderScenario scenario = new OrderScenario();
        Order paid = scenario.paidOrder("ORDER-003");

        Order duplicate = scenario.application().pay(paid.id(), "payment-ORDER-003");

        assertEquals(OrderStatus.PAID, duplicate.status());
        assertEquals(3, scenario.adapters().pending(10).size());
    }

    @Test
    void shouldPersistOrderAndOutboxWithinTransactionBoundary() {
        InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
        AtomicInteger transactions = new AtomicInteger();
        OrderTransactionPort transaction = operation -> {
            transactions.incrementAndGet();
            operation.run();
        };
        OrderApplicationService application = new OrderApplicationService(
                adapters, adapters, adapters, adapters, transaction);

        application.create(new CreateOrderCommand("ORDER-004", "buyer-4", "Taylor"));

        assertEquals(1, transactions.get());
        assertEquals(1, adapters.pending(10).size());
    }
}
