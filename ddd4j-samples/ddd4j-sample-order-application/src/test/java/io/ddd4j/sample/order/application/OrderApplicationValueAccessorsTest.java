package io.ddd4j.sample.order.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OrderApplicationValueAccessorsTest {

    @Test
    void orderCommandsRetainBothAccessorForms() {
        CreateOrderCommand create = new CreateOrderCommand("order-1", "buyer-1", "Buyer");
        assertEquals(create.orderNo(), create.getOrderNo());
        assertEquals(create.buyerId(), create.getBuyerId());
        assertEquals(create.buyerName(), create.getBuyerName());

        AddOrderLineCommand add = new AddOrderLineCommand(
                "order-1", "goods-1", "Goods", 2, new BigDecimal("12.50"));
        assertEquals(add.orderId(), add.getOrderId());
        assertEquals(add.goodsId(), add.getGoodsId());
        assertEquals(add.goodsName(), add.getGoodsName());
        assertEquals(add.quantity(), add.getQuantity());
        assertEquals(add.unitPrice(), add.getUnitPrice());
    }

    @Test
    void outboxValuesRetainBothAccessorForms() {
        OutboxDispatchResult result = new OutboxDispatchResult(3, 2, 1);
        assertEquals(result.attempted(), result.getAttempted());
        assertEquals(result.published(), result.getPublished());
        assertEquals(result.failed(), result.getFailed());

        Object payload = new Object();
        Instant occurredAt = Instant.parse("2026-10-15T00:00:00Z");
        OutboxMessage message = new OutboxMessage("message-1", "order-1", "OrderCreated", payload, occurredAt);
        assertEquals(message.id(), message.getId());
        assertEquals(message.aggregateId(), message.getAggregateId());
        assertEquals(message.eventType(), message.getEventType());
        assertSame(message.payload(), message.getPayload());
        assertEquals(message.occurredAt(), message.getOccurredAt());
    }
}
