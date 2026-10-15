/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin.cqrs.order.web;

import io.ddd4j.sample.javalin.cqrs.order.web.OrderController.AddOrderLineRequest;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderController.CreateOrderRequest;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderController.OrderResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderDtoSubclassContractTest {

    @Test
    void orderDtosShouldPreserveSubclassEqualityExtensionPoint() throws Exception {
        assertSubclassContract(OrderResponse.class);
        assertSubclassContract(CreateOrderRequest.class);
        assertSubclassContract(AddOrderLineRequest.class);
        assertSubclassContract(OrderCQRSQueryController.OrderResponse.class);
    }

    @Test
    void inheritedRequestEqualityShouldRemainSymmetric() {
        CreateOrderRequest base = new CreateOrderRequest("O-1", "B-1", "Buyer");
        CreateOrderRequest subclass = new ExtendedCreateOrderRequest("O-1", "B-1", "Buyer");
        assertEquals(base, subclass);
        assertEquals(subclass, base);
        assertEquals(base.hashCode(), subclass.hashCode());
    }

    private static void assertSubclassContract(Class<?> type) throws Exception {
        assertFalse(Modifier.isFinal(type.getModifiers()), type.getName());
        Method method = type.getDeclaredMethod("canEqual", Object.class);
        assertTrue(Modifier.isProtected(method.getModifiers()), type.getName());
    }

    private static final class ExtendedCreateOrderRequest extends CreateOrderRequest {
        private ExtendedCreateOrderRequest(String orderNo, String buyerId, String buyerName) {
            super(orderNo, buyerId, buyerName);
        }
    }
}
