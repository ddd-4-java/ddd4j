/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin.cqrs.order.application;

import tools.jackson.databind.ObjectMapper;
import io.ddd4j.sample.javalin.cqrs.order.application.OrderApplicationService.AddOrderLineCommand;
import io.ddd4j.sample.javalin.cqrs.order.application.OrderApplicationService.CreateOrderCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OrderCommandContractTest {

    @Test
    void createOrder_shouldExposeBeanAndComponentAccessorsWithValueAndJsonContract() throws Exception {
        CreateOrderCommand command = new CreateOrderCommand("O-1", "B-1", "Buyer");
        CreateOrderCommand equal = new CreateOrderCommand("O-1", "B-1", "Buyer");
        assertEquals(command.orderNo(), command.getOrderNo());
        assertEquals(command.buyerId(), command.getBuyerId());
        assertEquals(command.buyerName(), command.getBuyerName());
        assertEquals(equal, command);
        assertEquals(equal.hashCode(), command.hashCode());
        assertNotEquals(command, new CreateOrderCommand("O-2", "B-1", "Buyer"));
        assertEquals("CreateOrderCommand[orderNo=O-1, buyerId=B-1, buyerName=Buyer]", command.toString());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(command, mapper.readValue(mapper.writeValueAsString(command), CreateOrderCommand.class));
    }

    @Test
    void addLine_shouldExposeBeanAndComponentAccessorsWithValueAndJsonContract() throws Exception {
        AddOrderLineCommand command = new AddOrderLineCommand("O-1", "G-1", "Goods", 2, new BigDecimal("3.00"));
        AddOrderLineCommand equal = new AddOrderLineCommand("O-1", "G-1", "Goods", 2, new BigDecimal("3.00"));
        assertEquals(command.orderId(), command.getOrderId());
        assertEquals(command.goodsId(), command.getGoodsId());
        assertEquals(command.goodsName(), command.getGoodsName());
        assertEquals(command.quantity(), command.getQuantity());
        assertEquals(command.unitPrice(), command.getUnitPrice());
        assertEquals(equal, command);
        assertEquals(equal.hashCode(), command.hashCode());
        assertNotEquals(command, new AddOrderLineCommand("O-1", "G-1", "Goods", 3, new BigDecimal("3.00")));
        assertEquals("AddOrderLineCommand[orderId=O-1, goodsId=G-1, goodsName=Goods, quantity=2, unitPrice=3.00]", command.toString());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(command, mapper.readValue(mapper.writeValueAsString(command), AddOrderLineCommand.class));
    }
}
