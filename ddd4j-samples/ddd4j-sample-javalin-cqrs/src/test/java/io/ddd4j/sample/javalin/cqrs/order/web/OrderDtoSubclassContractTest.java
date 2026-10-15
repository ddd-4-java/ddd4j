/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin.cqrs.order.web;

import io.ddd4j.sample.javalin.cqrs.order.domain.model.Money;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderController.AddOrderLineRequest;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderController.CreateOrderRequest;
import io.ddd4j.sample.javalin.cqrs.order.web.OrderController.OrderResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;

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

    @Test
    void overriddenGettersShouldParticipateInValueEquality() {
        assertSameValue(new CreateOrderRequest("O-2", "B-1", "Buyer"),
                new GetterCreateOrderRequest("O-1", "B-1", "Buyer"));
        assertSameValue(new AddOrderLineRequest("G-2", "Goods", 2, BigDecimal.TEN),
                new GetterAddOrderLineRequest("G-1", "Goods", 2, BigDecimal.TEN));
        assertSameValue(new OrderResponse("1", "O-2", "B-1", "Buyer", "PAID", null, 2),
                new GetterOrderResponse("1", "O-1", "B-1", "Buyer", "PAID", null, 2));
        assertSameValue(new OrderCQRSQueryController.OrderResponse("1", "O-2", "B-1", "Buyer", "PAID", null, 2),
                new GetterQueryOrderResponse("1", "O-1", "B-1", "Buyer", "PAID", null, 2));
    }

    private static void assertSameValue(Object base, Object subclass) {
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

    private static final class GetterCreateOrderRequest extends CreateOrderRequest {
        private GetterCreateOrderRequest(String orderNo, String buyerId, String buyerName) {
            super(orderNo, buyerId, buyerName);
        }

        @Override
        public String getOrderNo() {
            return "O-2";
        }
    }

    private static final class GetterAddOrderLineRequest extends AddOrderLineRequest {
        private GetterAddOrderLineRequest(String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
            super(goodsId, goodsName, quantity, unitPrice);
        }

        @Override
        public String getGoodsId() {
            return "G-2";
        }
    }

    private static final class GetterOrderResponse extends OrderResponse {
        private GetterOrderResponse(String id, String orderNo, String buyerId, String buyerName,
                                    String status, Money totalAmount,
                                    int lineCount) {
            super(id, orderNo, buyerId, buyerName, status, totalAmount, lineCount);
        }

        @Override
        public String getOrderNo() {
            return "O-2";
        }
    }

    private static final class GetterQueryOrderResponse extends OrderCQRSQueryController.OrderResponse {
        private GetterQueryOrderResponse(String id, String orderNo, String buyerId, String buyerName,
                                         String status, Money totalAmount,
                                         int lineCount) {
            super(id, orderNo, buyerId, buyerName, status, totalAmount, lineCount);
        }

        @Override
        public String getOrderNo() {
            return "O-2";
        }
    }
}
