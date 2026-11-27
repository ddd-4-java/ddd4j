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

/** \u901a\u8fc7 <code>lifecycle.register()</code>\u3001<code>closed.add()</code>\u3001<code>lifecycle.close()</code> \u9a8c\u8bc1 <code>MQClientLifecycle</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class MQClientLifecycleTest {

    /** \u9a8c\u8bc1<code>closed</code> \u7b49\u4e8e <code>Arrays.asList("consumer", "connection")</code>\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>lifecycle.register()</code>\u3001<code>closed.add()</code>\u3001<code>lifecycle.checkpoint()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>successfulClose.get()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>exception.getSuppressed().length</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void continuesClosingAndAggregatesFailures() {
        MQClientLifecycle lifecycle = new MQClientLifecycle();
        AtomicInteger successfulClose = new AtomicInteger();
        lifecycle.register("successful", successfulClose::incrementAndGet);
        lifecycle.register("first-failure", () -> {
            throw new IllegalStateException("first");
        });
        lifecycle.register("second-failure", () -> {
            throw new IllegalArgumentException("second");
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class, lifecycle::close);

        assertEquals(1, successfulClose.get());
        assertEquals(2, exception.getSuppressed().length);
    }
}
