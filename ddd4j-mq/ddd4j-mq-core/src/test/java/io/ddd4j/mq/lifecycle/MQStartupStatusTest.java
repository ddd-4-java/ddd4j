/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import io.ddd4j.core.health.ReadinessResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MQStartupStatusTest {

    @Test
    void mapsReadyAndDegradedStatesToReadiness() {
        MQStartupStatus status = new MQStartupStatus("kafka");
        MQReadinessContributor contributor = new MQReadinessContributor(status);
        status.starting();
        status.ready();

        assertTrue(contributor.check().ready());

        status.degraded(failure(false));
        ReadinessResult result = contributor.check();
        assertFalse(result.ready());
        assertEquals("DEGRADED", result.details().get("state"));
    }

    @Test
    void exposesImmutableFailureSnapshot() {
        MQStartupStatus status = new MQStartupStatus("rabbit");
        status.failed(failure(true));
        MQStartupStatus.Snapshot snapshot = status.snapshot();

        assertEquals(MQStartupState.FAILED, snapshot.state());
        assertEquals(1, snapshot.failures().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.failures().add(failure(false)));
    }

    private MQListenerInitializationFailure failure(boolean required) {
        return new MQListenerInitializationFailure(
                "kafka", "orders", "billing", "OrderListener#consume", required, "connection refused");
    }
}
