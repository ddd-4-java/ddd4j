/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.auth.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

