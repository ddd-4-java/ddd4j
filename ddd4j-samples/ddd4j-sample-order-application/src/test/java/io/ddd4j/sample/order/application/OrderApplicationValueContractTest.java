package io.ddd4j.sample.order.application;

import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.sample.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderApplicationValueContractTest {

    @Test
    void commandsRetainBeanAndComponentReaders() {
        CreateOrderCommand create = new CreateOrderCommand("O-1", "B-1", "Alice");
        assertEquals(create.getOrderNo(), create.orderNo());
        assertEquals(create.getBuyerId(), create.buyerId());
        assertEquals(create.getBuyerName(), create.buyerName());
        CreateOrderCommand sameCreate = new CreateOrderCommand("O-1", "B-1", "Alice");
        assertEquals(sameCreate, create);
        assertEquals(sameCreate.hashCode(), create.hashCode());
        assertTrue(create.toString().startsWith("CreateOrderCommand("));

        AddOrderLineCommand add = new AddOrderLineCommand("id-1", "G-1", "Coffee", 2,
                new BigDecimal("12.50"));
        assertEquals(add.getOrderId(), add.orderId());
        assertEquals(add.getGoodsId(), add.goodsId());
        assertEquals(add.getGoodsName(), add.goodsName());
        assertEquals(add.getQuantity(), add.quantity());
        assertEquals(add.getUnitPrice(), add.unitPrice());
        AddOrderLineCommand sameAdd = new AddOrderLineCommand("id-1", "G-1", "Coffee", 2,
                new BigDecimal("12.50"));
        assertEquals(sameAdd, add);
        assertEquals(sameAdd.hashCode(), add.hashCode());
        assertTrue(add.toString().startsWith("AddOrderLineCommand("));
    }

    @Test
    void readModelRetainsValueAndReadContracts() {
        OrderReadModel model = new OrderReadModel("id-1", "O-1", "B-1", "Alice",
                OrderStatus.DRAFT, new BigDecimal("25.00"));
        assertEquals(model.getId(), model.id());
        assertEquals(model.getOrderNo(), model.orderNo());
        assertEquals(model.getBuyerId(), model.buyerId());
        assertEquals(model.getBuyerName(), model.buyerName());
        assertEquals(model.getStatus(), model.status());
        assertEquals(model.getTotalAmount(), model.totalAmount());
        OrderReadModel same = new OrderReadModel("id-1", "O-1", "B-1", "Alice",
                OrderStatus.DRAFT, new BigDecimal("25.00"));
        assertEquals(same, model);
        assertEquals(same.hashCode(), model.hashCode());
        assertTrue(model.toString().startsWith("OrderReadModel("));
    }

    @Test
    void readModelJsonRetainsFieldsAndRoundTrips() {
        OrderReadModel model = new OrderReadModel("id-1", "O-1", "B-1", "Alice",
                OrderStatus.DRAFT, new BigDecimal("25.00"));
        String json = JsonKit.toJson(model);
        Map<String, Object> fields = JsonKit.toMap(json);
        assertNotNull(fields);
        assertEquals(6, fields.size());
        assertEquals("id-1", fields.get("id"));
        assertEquals("O-1", fields.get("orderNo"));
        assertEquals("B-1", fields.get("buyerId"));
        assertEquals("Alice", fields.get("buyerName"));
        assertEquals("DRAFT", fields.get("status"));
        assertEquals(0, new BigDecimal(fields.get("totalAmount").toString())
                .compareTo(new BigDecimal("25.00")));
        assertEquals(model, JsonKit.toObject(json, OrderReadModel.class));
    }

    @Test
    void createCommandJsonRoundTrips() {
        CreateOrderCommand command = new CreateOrderCommand("O-1", "B-1", "Alice");
        assertEquals(command, JsonKit.toObject(JsonKit.toJson(command), CreateOrderCommand.class));
    }

    @Test
    void addLineCommandJsonRoundTrips() {
        AddOrderLineCommand command = new AddOrderLineCommand("id-1", "G-1", "Coffee", 2,
                new BigDecimal("12.50"));
        assertEquals(command, JsonKit.toObject(JsonKit.toJson(command), AddOrderLineCommand.class));
    }

    @Test
    void dispatchResultJsonRoundTrips() {
        OutboxDispatchResult result = new OutboxDispatchResult(3, 2, 1);
        assertEquals(result, JsonKit.toObject(JsonKit.toJson(result), OutboxDispatchResult.class));
    }

    @Test
    void outboxMessageJsonRoundTrips() {
        OutboxMessage message = new OutboxMessage("msg-1", "id-1", "OrderCreated", "{}",
                Instant.parse("2026-01-01T00:00:00Z"));
        String json = JsonKit.toJson(message);
        assertEquals("2026-01-01T00:00:00Z", JsonKit.toMap(json).get("occurredAt"));
        assertEquals(message, JsonKit.toObject(json, OutboxMessage.class));
    }

    @Test
    void outboxObjectsRetainValueAndReadContracts() {
        OutboxDispatchResult result = new OutboxDispatchResult(3, 2, 1);
        assertEquals(result.getAttempted(), result.attempted());
        assertEquals(result.getPublished(), result.published());
        assertEquals(result.getFailed(), result.failed());
        OutboxDispatchResult sameResult = new OutboxDispatchResult(3, 2, 1);
        assertEquals(sameResult, result);
        assertEquals(sameResult.hashCode(), result.hashCode());
        assertTrue(result.toString().startsWith("OutboxDispatchResult("));

        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");
        OutboxMessage message = new OutboxMessage("msg-1", "id-1", "OrderCreated", "{}", occurredAt);
        assertEquals(message.getId(), message.id());
        assertEquals(message.getAggregateId(), message.aggregateId());
        assertEquals(message.getEventType(), message.eventType());
        assertEquals(message.getPayload(), message.payload());
        assertEquals(message.getOccurredAt(), message.occurredAt());
        OutboxMessage sameMessage = new OutboxMessage("msg-1", "id-1", "OrderCreated", "{}", occurredAt);
        assertEquals(sameMessage, message);
        assertEquals(sameMessage.hashCode(), message.hashCode());
        assertTrue(message.toString().startsWith("OutboxMessage("));
    }

    @Test
    void valuesRetainThreeLineHashContract() {
        BigDecimal price = new BigDecimal("12.50");
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        assertEquals(valueHash("O-1", null, "Alice"),
                new CreateOrderCommand("O-1", null, "Alice").hashCode());
        assertEquals(valueHash(2, "id-1", null, "Coffee", price),
                new AddOrderLineCommand("id-1", null, "Coffee", 2, price).hashCode());
        assertEquals(valueHash("id-1", "O-1", null, "Alice", OrderStatus.DRAFT, price),
                new OrderReadModel("id-1", "O-1", null, "Alice", OrderStatus.DRAFT, price).hashCode());
        assertEquals(valueHash("msg-1", "id-1", "OrderCreated", "{}", occurredAt),
                new OutboxMessage("msg-1", "id-1", "OrderCreated", "{}", occurredAt).hashCode());
        assertEquals(valueHash(3, 2, 1), new OutboxDispatchResult(3, 2, 1).hashCode());
    }

    private static int valueHash(Object... fields) {
        int result = 1;
        for (Object field : fields) {
            result = 59 * result + (Objects.isNull(field) ? 43 : field.hashCode());
        }
        return result;
    }
}
