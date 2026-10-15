/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin.cqrs.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderController.AddOrderLineRequest;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderController.CreateOrderRequest;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderController.OrderResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderDtoContractTest {

    @Test
    void responseHash_shouldMatchCrossLineValueContract() {
        assertEquals(-195058945, new OrderResponse("1", "O-1", "B-1", "Buyer", "PAID", null, 2).hashCode());
    }

    @Test
    void createRequestHash_shouldMatchCrossLineValueContract() {
        assertEquals(337890771, new CreateOrderRequest("O-1", "B-1", "Buyer").hashCode());
    }

    @Test
    void addLineRequestHash_shouldMatchCrossLineValueContract() {
        assertEquals(30344502, new AddOrderLineRequest("G-1", "Goods", 2, new BigDecimal("10.00")).hashCode());
    }

    @Test
    void response_shouldExposeBeanAndComponentAccessorsWithJsonRoundTrip() throws Exception {
        OrderResponse response = new OrderResponse("1", "O-1", "B-1", "Buyer", "PAID", null, 2);

        assertEquals(response.id(), response.getId());
        assertEquals(response.orderNo(), response.getOrderNo());
        assertEquals(response.buyerId(), response.getBuyerId());
        assertEquals(response.buyerName(), response.getBuyerName());
        assertEquals(response.status(), response.getStatus());
        assertEquals(response.totalAmount(), response.getTotalAmount());
        assertEquals(response.lineCount(), response.getLineCount());
        assertEquals("OrderResponse[id=1, orderNo=O-1, buyerId=B-1, buyerName=Buyer, status=PAID, totalAmount=null, lineCount=2]", response.toString());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(response, mapper.readValue(mapper.writeValueAsString(response), OrderResponse.class));
    }

    @Test
    void createRequest_shouldPreserveBeanMutationAndComponentAccessors() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("O-1", "B-1", "Buyer");

        assertEquals(request.orderNo(), request.getOrderNo());
        assertEquals(request.buyerId(), request.getBuyerId());
        assertEquals(request.buyerName(), request.getBuyerName());

        request.setOrderNo("O-2");
        request.setBuyerId("B-2");
        request.setBuyerName("Next");
        CreateOrderRequest fromBean = new CreateOrderRequest();
        fromBean.setOrderNo("O-2");
        fromBean.setBuyerId("B-2");
        fromBean.setBuyerName("Next");

        assertEquals("O-2", request.orderNo());
        assertEquals("B-2", request.buyerId());
        assertEquals("Next", request.buyerName());
        assertEquals(request, fromBean);
        assertEquals(request.hashCode(), fromBean.hashCode());
        assertEquals("CreateOrderRequest[orderNo=O-2, buyerId=B-2, buyerName=Next]", request.toString());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request), CreateOrderRequest.class));
    }

    @Test
    void addLineRequest_shouldPreserveBeanMutationAndComponentAccessors() throws Exception {
        AddOrderLineRequest request = new AddOrderLineRequest("G-1", "Goods", 2, new BigDecimal("10.00"));

        assertEquals(request.goodsId(), request.getGoodsId());
        assertEquals(request.goodsName(), request.getGoodsName());
        assertEquals(request.quantity(), request.getQuantity());
        assertEquals(request.unitPrice(), request.getUnitPrice());

        request.setGoodsId("G-2");
        request.setGoodsName("Other");
        request.setQuantity(3);
        request.setUnitPrice(new BigDecimal("12.00"));
        AddOrderLineRequest fromBean = new AddOrderLineRequest();
        fromBean.setGoodsId("G-2");
        fromBean.setGoodsName("Other");
        fromBean.setQuantity(3);
        fromBean.setUnitPrice(new BigDecimal("12.00"));

        assertEquals("G-2", request.goodsId());
        assertEquals("Other", request.goodsName());
        assertEquals(3, request.quantity());
        assertEquals(new BigDecimal("12.00"), request.unitPrice());
        assertEquals(request, fromBean);
        assertEquals(request.hashCode(), fromBean.hashCode());
        assertEquals("AddOrderLineRequest[goodsId=G-2, goodsName=Other, quantity=3, unitPrice=12.00]", request.toString());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request), AddOrderLineRequest.class));
    }
}
