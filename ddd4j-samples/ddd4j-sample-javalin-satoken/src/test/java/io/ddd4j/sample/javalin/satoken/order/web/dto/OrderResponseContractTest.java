package io.ddd4j.sample.javalin.satoken.order.web.dto;

import io.ddd4j.sample.javalin.satoken.order.domain.model.Order;
import io.ddd4j.sample.javalin.satoken.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderResponseContractTest {

    @Test
    void responseRetainsBeanAndComponentReadersWithNestedValueSemantics() {
        OrderResponse.OrderLineResponse line = new OrderResponse.OrderLineResponse(
                "L-1", "G-1", "Coffee", 2, "12.50", "CNY");
        assertEquals(line.getId(), line.id());
        assertEquals(line.getGoodsId(), line.goodsId());
        assertEquals(line.getGoodsName(), line.goodsName());
        assertEquals(line.getQuantity(), line.quantity());
        assertEquals(line.getUnitPrice(), line.unitPrice());
        assertEquals(line.getCurrency(), line.currency());
        OrderResponse.OrderLineResponse sameLine = new OrderResponse.OrderLineResponse(
                "L-1", "G-1", "Coffee", 2, "12.50", "CNY");
        assertEquals(sameLine, line);
        assertEquals(sameLine.hashCode(), line.hashCode());
        assertEquals(sameLine.toString(), line.toString());
        assertTrue(line.toString().startsWith("OrderResponse.OrderLineResponse("));

        OrderResponse response = new OrderResponse("id-1", "O-1", "B-1", "Alice",
                OrderStatus.DRAFT, "25.00", "CNY", Arrays.asList(line));
        assertEquals(response.getId(), response.id());
        assertEquals(response.getOrderNo(), response.orderNo());
        assertEquals(response.getBuyerId(), response.buyerId());
        assertEquals(response.getBuyerName(), response.buyerName());
        assertEquals(response.getStatus(), response.status());
        assertEquals(response.getTotalAmount(), response.totalAmount());
        assertEquals(response.getCurrency(), response.currency());
        assertEquals(response.getLines(), response.lines());
        OrderResponse same = new OrderResponse("id-1", "O-1", "B-1", "Alice",
                OrderStatus.DRAFT, "25.00", "CNY", Arrays.asList(sameLine));
        assertEquals(same, response);
        assertEquals(same.hashCode(), response.hashCode());
        assertEquals(same.toString(), response.toString());
        assertTrue(response.toString().startsWith("OrderResponse("));
    }

    @Test
    void responseLinesCannotBeMutatedAfterConversion() {
        Order order = new Order("ORDER-1", "NO-1", "BUYER-1", "Ada",
                OrderStatus.DRAFT, Collections.emptyList());
        OrderResponse response = OrderResponse.from(order);

        assertThrows(UnsupportedOperationException.class, () -> response.lines().add(null));
    }
}
