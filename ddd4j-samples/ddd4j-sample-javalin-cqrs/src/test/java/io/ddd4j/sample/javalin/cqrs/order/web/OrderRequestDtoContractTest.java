package io.ddd4j.sample.javalin.cqrs.order.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderRequestDtoContractTest {

    @Test
    void createOrderRequestSupportsMutableBeanAndComponentAccess() {
        OrderController.CreateOrderRequest request = new OrderController.CreateOrderRequest();
        request.setOrderNo("order-1");
        request.setBuyerId("buyer-1");
        request.setBuyerName("Buyer");

        assertEquals(request.orderNo(), request.getOrderNo());
        assertEquals(request.buyerId(), request.getBuyerId());
        assertEquals(request.buyerName(), request.getBuyerName());
        assertEquals(new OrderController.CreateOrderRequest("order-1", "buyer-1", "Buyer"), request);
    }

    @Test
    void addOrderLineRequestSupportsMutableBeanAndComponentAccess() {
        OrderController.AddOrderLineRequest request = new OrderController.AddOrderLineRequest();
        request.setGoodsId("goods-1");
        request.setGoodsName("Goods");
        request.setQuantity(2);
        request.setUnitPrice(new BigDecimal("12.50"));

        assertEquals(request.goodsId(), request.getGoodsId());
        assertEquals(request.goodsName(), request.getGoodsName());
        assertEquals(request.quantity(), request.getQuantity());
        assertEquals(request.unitPrice(), request.getUnitPrice());
        assertEquals(new OrderController.AddOrderLineRequest("goods-1", "Goods", 2, new BigDecimal("12.50")), request);
    }
}
