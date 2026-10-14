package io.ddd4j.sample.javalin.cqrs.order.web;

import io.ddd4j.sample.javalin.cqrs.order.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderResponseDtoContractTest {

    @Test
    void commandResponseExposesBeanAndComponentAccessors() {
        Money amount = new Money(new BigDecimal("12.50"), "CNY");
        OrderController.OrderResponse response = new OrderController.OrderResponse(
                "id-1", "order-1", "buyer-1", "Buyer", "CREATED", amount, 2);
        assertEquals(response.getId(), response.id());
        assertEquals(response.getOrderNo(), response.orderNo());
        assertEquals(response.getBuyerId(), response.buyerId());
        assertEquals(response.getBuyerName(), response.buyerName());
        assertEquals(response.getStatus(), response.status());
        assertEquals(response.getTotalAmount(), response.totalAmount());
        assertEquals(response.getLineCount(), response.lineCount());
    }

    @Test
    void queryResponseExposesBeanAndComponentAccessors() {
        Money amount = new Money(new BigDecimal("12.50"), "CNY");
        OrderCQRSQueryController.OrderResponse response = new OrderCQRSQueryController.OrderResponse(
                "id-1", "order-1", "buyer-1", "Buyer", "CREATED", amount, 2);
        assertEquals(response.getId(), response.id());
        assertEquals(response.getOrderNo(), response.orderNo());
        assertEquals(response.getBuyerId(), response.buyerId());
        assertEquals(response.getBuyerName(), response.buyerName());
        assertEquals(response.getStatus(), response.status());
        assertEquals(response.getTotalAmount(), response.totalAmount());
        assertEquals(response.getLineCount(), response.lineCount());
    }
}
