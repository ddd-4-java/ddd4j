/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MQClientLifecycleTest {

    @Test
    void closesInReverseOrderOnlyOnce() {
        MQClientLifecycle lifecycle = new MQClientLifecycle();
        List<String> closed = new ArrayList<>();
        lifecycle.register("connection", () -> closed.add("connection"));
        lifecycle.register("consumer", () -> closed.add("consumer"));

        lifecycle.close();
        lifecycle.close();

        assertEquals(Arrays.asList("consumer", "connection"), closed);
    }

    @Test
    void rollsBackOnlyResourcesCreatedAfterCheckpoint() {
        MQClientLifecycle lifecycle = new MQClientLifecycle();
        List<String> closed = new ArrayList<>();
        lifecycle.register("existing", () -> closed.add("existing"));
        int checkpoint = lifecycle.checkpoint();
        lifecycle.register("producer", () -> closed.add("producer"));
        lifecycle.register("consumer", () -> closed.add("consumer"));

        lifecycle.rollback(checkpoint);
        lifecycle.close();

        assertEquals(Arrays.asList("consumer", "producer", "existing"), closed);
    }

    @Test
    void continuesClosingAndAggregatesFailures() {
        MQClientLifecycle lifecycle = new MQClientLifecycle();
        AtomicInteger successfulClose = new AtomicInteger();
        lifecycle.register("successful", successfulClose::incrementAndGet);
        lifecycle.register("first-failure", () -> { throw new IllegalStateException("first"); });
        lifecycle.register("second-failure", () -> { throw new IllegalArgumentException("second"); });

        IllegalStateException exception = assertThrows(IllegalStateException.class, lifecycle::close);

        assertEquals(1, successfulClose.get());
        assertEquals(2, exception.getSuppressed().length);
    }
}
