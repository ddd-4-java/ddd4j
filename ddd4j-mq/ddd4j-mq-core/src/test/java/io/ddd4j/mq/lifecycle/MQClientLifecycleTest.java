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

/**
 * 通过 <code>lifecycle.register()</code>、<code>closed.add()</code>、<code>lifecycle.close()</code> 验证 <code>MQClientLifecycle</code> 的运行行为。
 */
class MQClientLifecycleTest {

    /**
     * 验证<code>closed</code> 等于 <code>Arrays.asList("consumer", "connection")</code>。
     */
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

    /**
     * 在测试框架中执行 <code>lifecycle.register()</code>、<code>closed.add()</code>、<code>lifecycle.checkpoint()</code>，验证该场景。
     */
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

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>successfulClose.get()</code> 等于 <code>1</code>，<code>exception.getSuppressed().length</code> 等于 <code>2</code>。
     */
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
