/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin.cqrs.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderCQRSQueryController.OrderResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderQueryResponseContractTest {

    @Test
    void responseHash_shouldMatchCrossLineValueContract() {
        assertEquals(-195058945, new OrderResponse("1", "O-1", "B-1", "Buyer", "PAID", null, 2).hashCode());
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
}
