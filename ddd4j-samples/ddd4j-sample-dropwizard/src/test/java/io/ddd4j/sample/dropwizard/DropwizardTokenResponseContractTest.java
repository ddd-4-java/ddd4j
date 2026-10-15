/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.sample.dropwizard;

import io.ddd4j.sample.dropwizard.DropwizardOrderResource.TokenResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线令牌响应对象的公开 Bean 与值语义契约。 */
class DropwizardTokenResponseContractTest {

    @Test
    void tokenResponseKeepsBeanAndComponentAccessors() {
        TokenResponse response = new TokenResponse("token-1");
        assertEquals("token-1", response.getToken());
        assertEquals(response.getToken(), response.token());
        assertEquals(response, new TokenResponse("token-1"));
        assertEquals(response.hashCode(), new TokenResponse("token-1").hashCode());
        assertNotEquals(response, new TokenResponse("token-2"));
        assertTrue(response.toString().contains("token=token-1"));
        response.setToken("token-3");
        assertEquals("token-3", response.getToken());
    }
}
