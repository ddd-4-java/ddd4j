/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.sample.vertx;

import io.ddd4j.sample.vertx.VertxOrderRoutes.TokenResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线 Vert.x 令牌响应值对象契约。 */
class VertxTokenResponseContractTest {

    @Test
    void tokenResponseKeepsBeanAndComponentAccessors() {
        TokenResponse response = new TokenResponse("token-1");
        assertEquals("token-1", response.getToken());
        assertEquals(response.getToken(), response.token());
        assertEquals(response, new TokenResponse("token-1"));
        assertEquals(response.hashCode(), new TokenResponse("token-1").hashCode());
        assertNotEquals(response, new TokenResponse("token-2"));
        assertTrue(response.toString().contains("token=token-1"));
    }
}
