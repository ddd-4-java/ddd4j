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
package io.ddd4j.sample.order.testkit;

import io.ddd4j.sample.order.application.*;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderStatus;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void shouldNotReleasePaymentKeyAfterCommittedOrderWhenCompletionFails() {
        InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
        AtomicInteger releases = new AtomicInteger();
        IdempotencyPort idempotency = new IdempotencyPort() {
            @Override
            public boolean acquire(String key, Duration ttl) {
                return adapters.acquire(key, ttl);
            }

            @Override
            public void complete(String key, Object result) {
                throw new IllegalStateException("idempotency confirmation unavailable");
            }

            @Override
            public void release(String key) {
                releases.incrementAndGet();
                adapters.release(key);
            }
        };
        OrderApplicationService application = new OrderApplicationService(
                adapters, adapters, adapters, idempotency, adapters);
        Order order = application.create(new CreateOrderCommand("ORDER-COMPLETE-FAIL", "buyer-1", "Alice"));
        application.addLine(new AddOrderLineCommand(order.id(), "goods-1", "DDD Book", 1,
                new BigDecimal("59.90")));

        assertThrows(IllegalStateException.class, () -> application.pay(order.id(), "payment-complete-fail"));
        assertEquals(OrderStatus.PAID, application.find(order.id()).status());
        assertEquals(0, releases.get());
        int outboxCount = adapters.pending(10).size();

        Order retry = application.pay(order.id(), "payment-complete-fail");

        assertEquals(OrderStatus.PAID, retry.status());
        assertEquals(outboxCount, adapters.pending(10).size());
    }

    @Test
    void shouldReleasePaymentKeyWhenPaymentFailsBeforePersistence() {
        OrderScenario scenario = new OrderScenario();
        Order order = scenario.application().create(
                new CreateOrderCommand("ORDER-PAY-RETRY", "buyer-1", "Alice"));

        assertThrows(IllegalStateException.class,
                () -> scenario.application().pay(order.id(), "payment-retry"));
        scenario.application().addLine(new AddOrderLineCommand(order.id(), "goods-1", "DDD Book", 1,
                new BigDecimal("59.90")));

        Order paid = scenario.application().pay(order.id(), "payment-retry");

        assertEquals(OrderStatus.PAID, paid.status());
    }

    @Test
    void shouldReadFromWriteModelWhenProjectionIsMissing() {
        InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
        OrderReadModelPort missingProjection = new OrderReadModelPort() {
            @Override
            public void project(OrderReadModel order) {
            }

            @Override
            public Optional<OrderReadModel> findProjectionById(String orderId) {
                return Optional.empty();
            }

            @Override
            public List<OrderReadModel> query(OrderQuery query) {
                return Collections.emptyList();
            }
        };
        OrderApplicationService application = new OrderApplicationService(
                adapters, adapters, missingProjection, adapters, adapters);
        Order created = application.create(new CreateOrderCommand("ORDER-FALLBACK", "buyer-fallback", "Alex"));
        application.addLine(new AddOrderLineCommand(created.id(), "goods-1", "DDD Book", 1,
                new BigDecimal("59.90")));
        application.pay(created.id(), "payment-fallback");

        OrderReadModel fallback = application.find(created.id());

        assertEquals(OrderStatus.PAID, fallback.status());
        assertEquals("59.90", fallback.totalAmount().toPlainString());
        assertEquals(created.id(), fallback.id());
        assertEquals("ORDER-FALLBACK", fallback.orderNo());
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
