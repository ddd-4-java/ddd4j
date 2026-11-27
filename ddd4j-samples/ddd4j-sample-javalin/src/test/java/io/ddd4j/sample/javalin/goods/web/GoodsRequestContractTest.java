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
     * 验证<code>request.code()</code> 等于 <code>"goods-1"</code>，<code>request.name()</code> 等于 <code>"Notebook"</code>，<code>restored.stock()</code> 等于 <code>request.stock()</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
     * 验证<code>request.name()</code> 等于 <code>"Updated"</code>，<code>restored.name()</code> 等于 <code>request.name()</code>，<code>restored.price()</code> 等于 <code>request.price()</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
