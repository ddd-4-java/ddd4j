/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.dropwizard.cqrs.web.OrderResource.CreateOrderRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线 Dropwizard CQRS 订单值对象的共同公开契约。 */
class DropwizardOrderValueContractTest {

    @Test
    void commandKeepsBeanAndComponentAccessorsAndValueSemantics() {
        CreateOrderCommand first = new CreateOrderCommand("O-1", "B-1", "Alice");
        CreateOrderCommand same = new CreateOrderCommand("O-1", "B-1", "Alice");
        CreateOrderCommand other = new CreateOrderCommand("O-2", "B-1", "Alice");
        assertEquals("O-1", first.getOrderNo());
        assertEquals("B-1", first.getBuyerId());
        assertEquals("Alice", first.getBuyerName());
        assertEquals(first.getOrderNo(), first.orderNo());
        assertEquals(first.getBuyerId(), first.buyerId());
        assertEquals(first.getBuyerName(), first.buyerName());
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, other);
        assertEquals(336683680, first.hashCode());
        assertEquals("CreateOrderCommand(orderNo=O-1, buyerId=B-1, buyerName=Alice)", first.toString());
    }

    @Test
    void requestKeepsMutableBeanAndComponentAccessors() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderNo("O-1");
        request.setBuyerId("B-1");
        request.setBuyerName("Alice");
        CreateOrderRequest same = new CreateOrderRequest("O-1", "B-1", "Alice");
        assertEquals("O-1", request.getOrderNo());
        assertEquals("B-1", request.getBuyerId());
        assertEquals("Alice", request.getBuyerName());
        assertEquals(request.getOrderNo(), request.orderNo());
        assertEquals(request.getBuyerId(), request.buyerId());
        assertEquals(request.getBuyerName(), request.buyerName());
        assertEquals(request, same);
        assertEquals(request.hashCode(), same.hashCode());
        assertTrue(request.toString().contains("orderNo=O-1"));
    }
}
