/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin.cqrs.goods.web;

import tools.jackson.databind.ObjectMapper;
import io.ddd4j.sample.javalin.cqrs.goods.web.GoodsController.CreateGoodsRequest;
import io.ddd4j.sample.javalin.cqrs.goods.web.GoodsController.UpdateGoodsRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoodsRequestContractTest {

    @Test
    void createRequest_shouldKeepBeanAndComponentAccessorsInSync() {
        CreateGoodsRequest request = new CreateGoodsRequest("A", "Apple", new BigDecimal("10.00"), 2);

        assertEquals(request.code(), request.getCode());
        assertEquals(request.name(), request.getName());
        assertEquals(request.price(), request.getPrice());
        assertEquals(request.stock(), request.getStock());

        request.setCode("B");
        request.setName("Banana");
        request.setPrice(new BigDecimal("12.00"));
        request.setStock(3);

        assertEquals("B", request.code());
        assertEquals("Banana", request.name());
        assertEquals(new BigDecimal("12.00"), request.price());
        assertEquals(3, request.stock());
        CreateGoodsRequest fromBean = new CreateGoodsRequest();
        fromBean.setCode("B");
        fromBean.setName("Banana");
        fromBean.setPrice(new BigDecimal("12.00"));
        fromBean.setStock(3);
        assertEquals(request, fromBean);
        assertEquals(request.hashCode(), fromBean.hashCode());
        assertEquals("CreateGoodsRequest[code=B, name=Banana, price=12.00, stock=3]", request.toString());
    }

    @Test
    void updateRequest_shouldKeepBeanAndComponentAccessorsInSync() {
        UpdateGoodsRequest request = new UpdateGoodsRequest("Apple", new BigDecimal("10.00"));

        assertEquals(request.name(), request.getName());
        assertEquals(request.price(), request.getPrice());

        request.setName("Banana");
        request.setPrice(new BigDecimal("12.00"));

        assertEquals("Banana", request.name());
        assertEquals(new BigDecimal("12.00"), request.price());
        UpdateGoodsRequest fromBean = new UpdateGoodsRequest();
        fromBean.setName("Banana");
        fromBean.setPrice(new BigDecimal("12.00"));
        assertEquals(request, fromBean);
        assertEquals(request.hashCode(), fromBean.hashCode());
        assertEquals("UpdateGoodsRequest[name=Banana, price=12.00]", request.toString());
    }

    @Test
    void requests_shouldRoundTripThroughJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CreateGoodsRequest create = new CreateGoodsRequest("A", "Apple", new BigDecimal("10.00"), 2);
        UpdateGoodsRequest update = new UpdateGoodsRequest("Apple", new BigDecimal("10.00"));

        assertEquals(create, mapper.readValue(mapper.writeValueAsString(create), CreateGoodsRequest.class));
        assertEquals(update, mapper.readValue(mapper.writeValueAsString(update), UpdateGoodsRequest.class));
    }
}
