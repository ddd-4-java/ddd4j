/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.sample.vertx.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.vertx.cqrs.command.CreateOrderCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线 Vert.x CQRS 创建订单命令契约。 */
class VertxOrderCommandContractTest {

    @Test
    void commandKeepsJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CreateOrderCommand command = new CreateOrderCommand("O-1", "B-1", "Alice");
        assertEquals(command, mapper.readValue(mapper.writeValueAsString(command), CreateOrderCommand.class));
    }

    @Test
    void commandKeepsBeanAndComponentAccessorsAndValueSemantics() {
        CreateOrderCommand first = new CreateOrderCommand("O-1", "B-1", "Alice");
        CreateOrderCommand same = new CreateOrderCommand("O-1", "B-1", "Alice");
        assertEquals("O-1", first.getOrderNo());
        assertEquals("B-1", first.getBuyerId());
        assertEquals("Alice", first.getBuyerName());
        assertEquals(first.getOrderNo(), first.orderNo());
        assertEquals(first.getBuyerId(), first.buyerId());
        assertEquals(first.getBuyerName(), first.buyerName());
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, new CreateOrderCommand("O-2", "B-1", "Alice"));
        assertEquals(336683680, first.hashCode());
        assertEquals("CreateOrderCommand(orderNo=O-1, buyerId=B-1, buyerName=Alice)", first.toString());
    }
}
