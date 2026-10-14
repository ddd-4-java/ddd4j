package io.ddd4j.sample.javalin.satoken.order.web.dto;

import io.ddd4j.sample.javalin.satoken.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OrderResponseAccessorsTest {

    @Test
    void lineResponseRetainsComponentAndBeanAccess() {
        OrderResponse.OrderLineResponse line =
                new OrderResponse.OrderLineResponse("line-1", "goods-1", "Goods", 2, "12.50", "CNY");
        assertEquals(line.id(), line.getId());
        assertEquals(line.goodsId(), line.getGoodsId());
        assertEquals(line.goodsName(), line.getGoodsName());
        assertEquals(line.quantity(), line.getQuantity());
        assertEquals(line.unitPrice(), line.getUnitPrice());
        assertEquals(line.currency(), line.getCurrency());
    }

    @Test
    void orderResponseRetainsComponentAndBeanAccess() {
        List<OrderResponse.OrderLineResponse> lines = Collections.emptyList();
        OrderResponse response = new OrderResponse(
                "order-1", "NO-1", "buyer-1", "Buyer", OrderStatus.DRAFT, "25.00", "CNY", lines);
        assertEquals(response.id(), response.getId());
        assertEquals(response.orderNo(), response.getOrderNo());
        assertEquals(response.buyerId(), response.getBuyerId());
        assertEquals(response.buyerName(), response.getBuyerName());
        assertEquals(response.status(), response.getStatus());
        assertEquals(response.totalAmount(), response.getTotalAmount());
        assertEquals(response.currency(), response.getCurrency());
        assertSame(response.lines(), response.getLines());
    }
}
