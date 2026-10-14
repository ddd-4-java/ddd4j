/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin.order.web;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.sample.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 订单 HTTP DTO 的三线公开方法与 JSON 契约。 */
class OrderRequestResponseContractTest {

    @Test
    void createRequestRoundTripsWithFluentAccessors() throws Exception {
        OrderController.CreateOrderRequest request = new OrderController.CreateOrderRequest(
                "order-1", "buyer-1", "Alice");
        assertEquals("order-1", request.orderNo());
        assertEquals("buyer-1", request.buyerId());
        assertEquals("Alice", request.buyerName());

        JsonMapper mapper = JsonMapper.builder().build();
        OrderController.CreateOrderRequest restored = mapper.readValue(
                mapper.writeValueAsString(request), OrderController.CreateOrderRequest.class);
        assertEquals(request.orderNo(), restored.orderNo());
        assertEquals(request.buyerId(), restored.buyerId());
        assertEquals(request.buyerName(), restored.buyerName());
    }

    @Test
    void addLineRequestRoundTripsWithFluentAccessors() throws Exception {
        OrderController.AddOrderLineRequest request = new OrderController.AddOrderLineRequest(
                "goods-1", "Notebook", 2, new BigDecimal("12.50"));
        assertEquals("goods-1", request.goodsId());
        assertEquals("Notebook", request.goodsName());
        assertEquals(2, request.quantity());
        assertEquals(new BigDecimal("12.50"), request.unitPrice());

        JsonMapper mapper = JsonMapper.builder().build();
        OrderController.AddOrderLineRequest restored = mapper.readValue(
                mapper.writeValueAsString(request), OrderController.AddOrderLineRequest.class);
        assertEquals(request.goodsId(), restored.goodsId());
        assertEquals(request.goodsName(), restored.goodsName());
        assertEquals(request.quantity(), restored.quantity());
        assertEquals(request.unitPrice(), restored.unitPrice());
    }

    @Test
    void responseRoundTripsWithFluentAccessors() throws Exception {
        OrderController.OrderResponse response = new OrderController.OrderResponse(
                "id-1", "order-1", "buyer-1", "Alice", OrderStatus.DRAFT,
                new BigDecimal("25.00"), "CNY", 2);
        assertEquals("id-1", response.id());
        assertEquals("order-1", response.orderNo());
        assertEquals("buyer-1", response.buyerId());
        assertEquals("Alice", response.buyerName());
        assertEquals(OrderStatus.DRAFT, response.status());
        assertEquals(new BigDecimal("25.00"), response.totalAmount());
        assertEquals("CNY", response.currency());
        assertEquals(2, response.lineCount());

        JsonMapper mapper = JsonMapper.builder().build();
        OrderController.OrderResponse restored = mapper.readValue(
                mapper.writeValueAsString(response), OrderController.OrderResponse.class);
        assertEquals(response.id(), restored.id());
        assertEquals(response.orderNo(), restored.orderNo());
        assertEquals(response.buyerId(), restored.buyerId());
        assertEquals(response.buyerName(), restored.buyerName());
        assertEquals(response.status(), restored.status());
        assertEquals(response.totalAmount(), restored.totalAmount());
        assertEquals(response.currency(), restored.currency());
        assertEquals(response.lineCount(), restored.lineCount());
    }
}
