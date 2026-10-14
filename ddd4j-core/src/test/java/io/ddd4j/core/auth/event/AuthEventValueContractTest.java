/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.auth.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class AuthEventValueContractTest {

    @Test
    void failedEvent_shouldMatchRecordValueSemanticsForNullComponents() {
        AuthFailedEvent event = new AuthFailedEvent(null, null, null);

        assertDoesNotThrow(event::hashCode);
        assertEquals(new AuthFailedEvent(null, null, null), event);
        assertEquals(0, event.hashCode());
        assertEquals("AuthFailedEvent[request=null, reason=null, occurredAt=null]", event.toString());
    }

    @Test
    void succeededEvent_shouldMatchRecordValueSemanticsForNullComponents() {
        AuthSucceededEvent event = new AuthSucceededEvent(null, null, null, null);

        assertDoesNotThrow(event::hashCode);
        assertEquals(new AuthSucceededEvent(null, null, null, null), event);
        assertEquals(0, event.hashCode());
        assertEquals("AuthSucceededEvent[request=null, principal=null, token=null, occurredAt=null]", event.toString());
    }

    @Test
    void failedEvent_shouldExposeBeanAndJsonConstructionContract() throws Exception {
        AuthFailedEvent event = new AuthFailedEvent(null, "invalid-token", Instant.EPOCH);

        assertEquals(event.request(), event.getRequest());
        assertEquals(event.reason(), event.getReason());
        assertEquals(event.occurredAt(), event.getOccurredAt());
        assertNotNull(AuthFailedEvent.class.getConstructor(AuthRequest.class, String.class, Instant.class)
                .getAnnotation(JsonCreator.class));
        assertEquals("reason", AuthFailedEvent.class.getMethod("reason")
                .getAnnotation(JsonProperty.class).value());
    }

    @Test
    void succeededEvent_shouldExposeBeanAndJsonConstructionContract() throws Exception {
        AuthSucceededEvent event = new AuthSucceededEvent(null, null, "valid-token", Instant.EPOCH);

        assertEquals(event.request(), event.getRequest());
        assertEquals(event.principal(), event.getPrincipal());
        assertEquals(event.token(), event.getToken());
        assertEquals(event.occurredAt(), event.getOccurredAt());
        assertNotNull(AuthSucceededEvent.class.getConstructor(
                AuthRequest.class, AuthPrincipal.class, String.class, Instant.class)
                .getAnnotation(JsonCreator.class));
        assertEquals("token", AuthSucceededEvent.class.getMethod("token")
                .getAnnotation(JsonProperty.class).value());
    }

    @Test
    void authEvents_shouldRoundTripThroughJsonWithStableFieldNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AuthFailedEvent failed = new AuthFailedEvent(null, "invalid-token", null);
        AuthSucceededEvent succeeded = new AuthSucceededEvent(null, null, "valid-token", null);

        String failedJson = mapper.writeValueAsString(failed);
        String succeededJson = mapper.writeValueAsString(succeeded);

        assertEquals(failed, mapper.readValue(failedJson, AuthFailedEvent.class));
        assertEquals(succeeded, mapper.readValue(succeededJson, AuthSucceededEvent.class));
        assertTrue(failedJson.contains("\"reason\":\"invalid-token\""));
        assertTrue(succeededJson.contains("\"token\":\"valid-token\""));
    }
}
