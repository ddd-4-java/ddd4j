/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** MQ客户端拥有资源的LIFO、幂等生命周期。 */
public final class MQClientLifecycle implements AutoCloseable {

    private static final MQClientLifecycle UNMANAGED = new MQClientLifecycle(false);

    private final Deque<Resource> resources = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final boolean managed;

    public MQClientLifecycle() {
        this(true);
    }

    private MQClientLifecycle(boolean managed) {
        this.managed = managed;
    }

    public static MQClientLifecycle unmanaged() {
        return UNMANAGED;
    }

    public synchronized void register(String name, Runnable closeAction) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(closeAction, "closeAction must not be null");
        if (!managed) {
            return;
        }
        if (closed.get()) {
            closeAction.run();
            return;
        }
        resources.push(new Resource(name, closeAction));
    }

    public synchronized int checkpoint() {
        return resources.size();
    }

    public synchronized void rollback(int checkpoint) {
        if (!managed) {
            return;
        }
        if (checkpoint < 0 || checkpoint > resources.size()) {
            throw new IllegalArgumentException("invalid MQ lifecycle checkpoint: " + checkpoint);
        }
        closeUntil(checkpoint);
    }

    @Override
    public synchronized void close() {
        if (!managed || !closed.compareAndSet(false, true)) {
            return;
        }
        closeUntil(0);
    }

    private void closeUntil(int targetSize) {
        IllegalStateException aggregate = null;
        while (resources.size() > targetSize) {
            Resource resource = resources.pop();
            try {
                resource.closeAction.run();
            } catch (RuntimeException exception) {
                if (aggregate == null) {
                    aggregate = new IllegalStateException("Failed to close one or more MQ resources");
                }
                aggregate.addSuppressed(new IllegalStateException(resource.name, exception));
            }
        }
        if (aggregate != null) {
            throw aggregate;
        }
    }

    private static final class Resource {
        private final String name;
        private final Runnable closeAction;

        private Resource(String name, Runnable closeAction) {
            this.name = name;
            this.closeAction = closeAction;
        }
    }
}
