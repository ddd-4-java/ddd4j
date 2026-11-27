/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.auth.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
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

    /** \u9a8c\u8bc1<code>event.hashCode()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>event.toString()</code> \u7b49\u4e8e <code>"AuthFailedEvent[request=null, reason=null, occurredAt=null]"</code>\u3002 */
    @Test
    void failedEvent_shouldMatchRecordValueSemanticsForNullComponents() {
        AuthFailedEvent event = new AuthFailedEvent(null, null, null);

        assertDoesNotThrow(event::hashCode);
        assertEquals(new AuthFailedEvent(null, null, null), event);
        assertEquals(0, event.hashCode());
        assertEquals("AuthFailedEvent[request=null, reason=null, occurredAt=null]", event.toString());
    }

    /** \u9a8c\u8bc1<code>event.hashCode()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>event.toString()</code> \u7b49\u4e8e <code>"AuthSucceededEvent[request=null, principal=null, token=null, occurredAt=null]"</code>\u3002 */
    @Test
    void succeededEvent_shouldMatchRecordValueSemanticsForNullComponents() {
        AuthSucceededEvent event = new AuthSucceededEvent(null, null, null, null);

        assertDoesNotThrow(event::hashCode);
        assertEquals(new AuthSucceededEvent(null, null, null, null), event);
        assertEquals(0, event.hashCode());
        assertEquals("AuthSucceededEvent[request=null, principal=null, token=null, occurredAt=null]", event.toString());
    }

    /**
     * \u9a8c\u8bc1<code>event.getRequest()</code> \u7b49\u4e8e <code>event.request()</code>\uff0c<code>event.getReason()</code> \u7b49\u4e8e <code>event.reason()</code>\uff0c<code>AuthFailedEvent.class.getMethod("reason") .getAnnotation(JsonProperty.class).value()</code> \u7b49\u4e8e <code>"reason"</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>event.getRequest()</code> \u7b49\u4e8e <code>event.request()</code>\uff0c<code>event.getPrincipal()</code> \u7b49\u4e8e <code>event.principal()</code>\uff0c<code>AuthSucceededEvent.class.getMethod("token") .getAnnotation(JsonProperty.class).value()</code> \u7b49\u4e8e <code>"token"</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>mapper.readValue(failedJson, AuthFailedEvent.class)</code> \u7b49\u4e8e <code>failed</code>\uff0c<code>mapper.readValue(succeededJson, AuthSucceededEvent.class)</code> \u7b49\u4e8e <code>succeeded</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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

