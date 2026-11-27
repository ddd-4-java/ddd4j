/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQ客户端拥有资源的LIFO、幂等生命周期。
 */
public final class MQClientLifecycle implements AutoCloseable {

    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private static final MQClientLifecycle UNMANAGED = new MQClientLifecycle(false);

    /** \u5f53\u524d\u751f\u547d\u5468\u671f\u8d1f\u8d23\u5173\u95ed\u5e76\u6309\u767b\u8bb0\u9006\u5e8f\u6e05\u7406\u7684\u8d44\u6e90\u6808\u3002 */
    private final Deque<Resource> resources = new ArrayDeque<>();
    /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
    private final AtomicBoolean closed = new AtomicBoolean();
    /** \u5f53\u524d\u751f\u547d\u5468\u671f\u662f\u5426\u542f\u7528\u4e86\u53d7\u7ba1\u542f\u52a8\u4e0e\u505c\u6b62\u3002 */
    private final boolean managed;

    /** \u521b\u5efa\u8d1f\u8d23\u5173\u95ed\u5df2\u6ce8\u518c\u8d44\u6e90\u7684\u751f\u547d\u5468\u671f\u7ba1\u7406\u5668\u3002 */
    public MQClientLifecycle() {
        this(true);
    }

    /**
     * \u521b\u5efa\u8ddf\u8e2a\u6d88\u606f\u4ee3\u7406\u8d44\u6e90\u7684\u751f\u547d\u5468\u671f\u3002
     * @param managed \u662f\u5426\u542f\u7528\u53d7\u7ba1\u751f\u547d\u5468\u671f
     */
    private MQClientLifecycle(boolean managed) {
        this.managed = managed;
    }

    /**
     * \u8fd4\u56de\u4e0d\u7ba1\u7406\u8d44\u6e90\u7684\u751f\u547d\u5468\u671f\u5b9e\u4f8b\u3002
     * @return \u975e\u6258\u7ba1\u751f\u547d\u5468\u671f\u5b9e\u4f8b
     */
    public static MQClientLifecycle unmanaged() {
        return UNMANAGED;
    }

    /**
     * \u6ce8\u518c\u5173\u95ed\u52a8\u4f5c\uff1b\u82e5\u5df2\u5173\u95ed\u5219\u7acb\u5373\u6267\u884c\u3002
     * @param name \u8d44\u6e90\u540d\u79f0
     * @param closeAction \u5173\u95ed\u52a8\u4f5c
     */
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

    /**
     * \u8bb0\u5f55\u5f53\u524d\u8d44\u6e90\u6570\u91cf\u4f5c\u4e3a\u56de\u6eda\u70b9\u3002
     * @return \u5f53\u524d\u56de\u6eda\u70b9
     */
    public synchronized int checkpoint() {
        return resources.size();
    }

    /**
     * \u5173\u95ed\u56de\u6eda\u70b9\u4e4b\u540e\u6ce8\u518c\u7684\u8d44\u6e90\u3002
     * @param checkpoint \u5148\u524d\u53d6\u5f97\u7684\u56de\u6eda\u70b9
     */
    public synchronized void rollback(int checkpoint) {
        if (!managed) {
            return;
        }
        if (checkpoint < 0 || checkpoint > resources.size()) {
            throw new IllegalArgumentException("invalid MQ lifecycle checkpoint: " + checkpoint);
        }
        closeUntil(checkpoint);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void close() {
        if (!managed || !closed.compareAndSet(false, true)) {
            return;
        }
        closeUntil(0);
    }

    /**
     * \u6309\u767b\u8bb0\u9006\u5e8f\u5173\u95ed\u6307\u5b9a\u68c0\u67e5\u70b9\u4e4b\u540e\u53d6\u5f97\u7684\u8d44\u6e90\uff0c\u5e76\u6c47\u603b\u5173\u95ed\u5f02\u5e38\u3002
     * @param targetSize \u6e05\u7406\u540e\u5e94\u4fdd\u7559\u7684\u8d44\u6e90\u6570\u91cf
     * @throws IllegalStateException \u81f3\u5c11\u4e00\u4e2a\u8d44\u6e90\u5173\u95ed\u52a8\u4f5c\u6267\u884c\u5931\u8d25
     */
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

    /** \u628a\u6d88\u606f\u5ba2\u6237\u7aef\u6301\u6709\u7684\u8d44\u6e90\u540d\u79f0\u4e0e\u5bf9\u5e94\u7684\u5173\u95ed\u52a8\u4f5c\u7ed1\u5b9a\u3002 */
    private static final class Resource {
        /** \u7528\u4e8e\u5173\u95ed\u5931\u8d25\u8bca\u65ad\u7684\u8d44\u6e90\u540d\u79f0\u3002 */
        private final String name;
        /** \u91ca\u653e\u4e00\u4e2a\u5df2\u767b\u8bb0\u6d88\u606f\u8d44\u6e90\u7684\u52a8\u4f5c\u3002 */
        private final Runnable closeAction;

        /**
         * \u5c06\u8d44\u6e90\u6807\u8bc6\u4e0e\u5176\u91ca\u653e\u52a8\u4f5c\u4e00\u8d77\u767b\u8bb0\u3002
         * @param name \u8d44\u6e90\u5728\u9519\u8bef\u8bca\u65ad\u4e2d\u7684\u540d\u79f0
         * @param closeAction \u91ca\u653e\u8be5\u8d44\u6e90\u7684\u56de\u8c03
         */
        private Resource(String name, Runnable closeAction) {
            this.name = name;
            this.closeAction = closeAction;
        }
    }
}
