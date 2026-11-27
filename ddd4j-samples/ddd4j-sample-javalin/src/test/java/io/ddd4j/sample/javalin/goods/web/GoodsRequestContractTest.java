/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin.goods.web;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 商品请求 DTO 的三线公开方法与 JSON 契约。
 */
class GoodsRequestContractTest {

    /**
     * \u9a8c\u8bc1<code>request.code()</code> \u7b49\u4e8e <code>"goods-1"</code>\uff0c<code>request.name()</code> \u7b49\u4e8e <code>"Notebook"</code>\uff0c<code>restored.stock()</code> \u7b49\u4e8e <code>request.stock()</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void createRequestKeepsFluentAccessorsAndJsonFields() throws Exception {
        GoodsController.CreateGoodsRequest request = new GoodsController.CreateGoodsRequest(
                "goods-1", "Notebook", new BigDecimal("12.50"), 8);
        assertEquals("goods-1", request.code());
        assertEquals("Notebook", request.name());
        assertEquals(new BigDecimal("12.50"), request.price());
        assertEquals(Integer.valueOf(8), request.stock());

        JsonMapper mapper = JsonMapper.builder().build();
        GoodsController.CreateGoodsRequest restored = mapper.readValue(
                mapper.writeValueAsString(request), GoodsController.CreateGoodsRequest.class);
        assertEquals(request.code(), restored.code());
        assertEquals(request.name(), restored.name());
        assertEquals(request.price(), restored.price());
        assertEquals(request.stock(), restored.stock());
    }

    /**
     * \u9a8c\u8bc1<code>request.name()</code> \u7b49\u4e8e <code>"Updated"</code>\uff0c<code>restored.name()</code> \u7b49\u4e8e <code>request.name()</code>\uff0c<code>restored.price()</code> \u7b49\u4e8e <code>request.price()</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void updateRequestKeepsFluentAccessorsAndJsonFields() throws Exception {
        GoodsController.UpdateGoodsRequest request = new GoodsController.UpdateGoodsRequest(
                "Updated", new BigDecimal("13.75"));
        assertEquals("Updated", request.name());
        assertEquals(new BigDecimal("13.75"), request.price());

        JsonMapper mapper = JsonMapper.builder().build();
        GoodsController.UpdateGoodsRequest restored = mapper.readValue(
                mapper.writeValueAsString(request), GoodsController.UpdateGoodsRequest.class);
        assertEquals(request.name(), restored.name());
        assertEquals(request.price(), restored.price());
    }
}
