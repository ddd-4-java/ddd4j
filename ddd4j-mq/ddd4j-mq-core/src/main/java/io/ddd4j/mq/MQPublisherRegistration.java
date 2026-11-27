/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** 标识一次发布器注册，并维护当前启动失败时可恢复的前驱。 */
final class MQPublisherRegistration implements Consumer<MQEvent> {

    /** 串行化 MQ 客户端的启动上下文和发布器注册操作。 */
    static final Object REGISTRY_LOCK = new Object();

    /** \u8ddf\u8e2a\u6d88\u606f\u53d1\u5e03\u5668\u6ce8\u518c\u4ece\u5f85\u63d0\u4ea4\u5230\u63d0\u4ea4\u3001\u5931\u8d25\u6216\u5173\u95ed\u7684\u9636\u6bb5\u3002 */
    private enum State {
        /** \u5c1a\u672a\u63d0\u4ea4\u7684\u53d1\u5e03\u5668\u6ce8\u518c\u3002 */
        PENDING,
            /** \u6ce8\u518c\u5df2\u6210\u529f\u63d0\u4ea4\uff0c\u53ef\u4f9b\u4e1a\u52a1\u4f7f\u7528\u3002 */
            COMMITTED,
            /** \u521d\u59cb\u5316\u672a\u5b8c\u6210\uff0c\u6ce8\u9500\u65f6\u5c1d\u8bd5\u6062\u590d\u65e7\u53d1\u5e03\u5668\u3002 */
            FAILED,
            /** \u6ce8\u518c\u5df2\u5173\u95ed\u4e14\u4e0d\u518d\u4f5c\u4e3a\u56de\u6eda\u5019\u9009\u3002 */
            CLOSED
    }

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u7684\u6ce8\u518c\u540d\u79f0\u3002 */
    private final String broker;
    /** \u5b9e\u9645\u5411\u5bf9\u5e94\u6d88\u606f\u4ee3\u7406\u53d1\u5e03\u4e8b\u4ef6\u7684\u56de\u8c03\u3002 */
    private final Consumer<MQEvent> delegate;
    /** DDD4J \u5171\u4eab\u4e0a\u4e0b\u6587\u4e2d\u7684\u4ee3\u7406\u53d1\u5e03\u5668\u6620\u5c04\u3002 */
    private final Map<String, Consumer<MQEvent>> publishers;
    /** \u5f53\u524d\u6ce8\u518c\u8986\u76d6\u524d\u540c\u4e00\u4ee3\u7406\u4f7f\u7528\u7684\u53d1\u5e03\u5668\u3002 */
    private Consumer<MQEvent> previous;
    /** \u8bb0\u5f55\u5f53\u524d\u542f\u52a8\u6216\u6ce8\u518c\u6d41\u7a0b\u6240\u5904\u7684\u72b6\u6001\u3002 */
    private State state = State.PENDING;
    /** \u6807\u8bb0\u5f53\u524d\u6ce8\u518c\u662f\u5426\u5df2\u7ecf\u6267\u884c\u6ce8\u9500\u3002 */
    private boolean unregistered;

    /**
     * \u5efa\u7acb\u5f53\u524d\u4ee3\u7406\u53d1\u5e03\u5668\u4e0e\u5171\u4eab\u6ce8\u518c\u8868\u4e4b\u95f4\u7684\u6ce8\u518c\u5173\u7cfb\u3002
     * @param broker \u4ee3\u7406\u6ce8\u518c\u952e
     * @param delegate \u53d1\u9001\u56de\u8c03
     * @param publishers \u5171\u4eab\u6ce8\u518c\u8868
     */
    private MQPublisherRegistration(String broker, Consumer<MQEvent> delegate,
                                    Map<String, Consumer<MQEvent>> publishers) {
        this.broker = broker;
        this.delegate = delegate;
        this.publishers = publishers;
    }

    /**
     * \u5728\u5171\u4eab\u6ce8\u518c\u8868\u4e2d\u4e3a\u4ee3\u7406\u5b89\u88c5\u53d1\u5e03\u5668\uff0c\u5e76\u8bb0\u5f55\u5b89\u88c5\u524d\u7684\u503c\u3002
     * @param broker \u4ee3\u7406\u6ce8\u518c\u952e
     * @param delegate \u53d1\u9001\u56de\u8c03
     * @return \u53ef\u63d0\u4ea4\u3001\u6807\u8bb0\u5931\u8d25\u5e76\u6ce8\u9500\u7684\u53d1\u5e03\u5668\u6ce8\u518c
     */
    static MQPublisherRegistration install(String broker, Consumer<MQEvent> delegate) {
        synchronized (REGISTRY_LOCK) {
            Map<String, Consumer<MQEvent>> publishers = publisherMap();
            MQPublisherRegistration registration = new MQPublisherRegistration(broker, delegate, publishers);
            registration.previous = publishers.put(broker, registration);
            return registration;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void accept(MQEvent event) {
        delegate.accept(event);
    }

    /** \u63d0\u4ea4\u4ecd\u5f85\u5b8c\u6210\u7684\u53d1\u5e03\u5668\u6ce8\u518c\u5e76\u91ca\u653e\u65e7\u503c\u5f15\u7528\u3002 */
    void commit() {
        synchronized (REGISTRY_LOCK) {
            if (state == State.PENDING) {
                state = State.COMMITTED;
                previous = null;
            }
        }
    }

    /** \u5c06\u5c1a\u672a\u63d0\u4ea4\u7684\u53d1\u5e03\u5668\u6ce8\u518c\u6807\u8bb0\u4e3a\u5931\u8d25\u3002 */
    void markFailed() {
        synchronized (REGISTRY_LOCK) {
            if (state == State.PENDING) {
                state = State.FAILED;
            }
        }
    }

    /** \u6ce8\u9500\u5f53\u524d\u6ce8\u518c\uff0c\u6ce8\u518c\u5931\u8d25\u65f6\u5c1d\u8bd5\u6062\u590d\u4ecd\u7136\u53ef\u7528\u7684\u65e7\u53d1\u5e03\u5668\u3002 */
    void unregister() {
        synchronized (REGISTRY_LOCK) {
            if (unregistered) {
                return;
            }
            unregistered = true;
            if (state == State.FAILED) {
                if (BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER) == publishers) {
                    Consumer<MQEvent> fallback = rollbackTarget();
                    if (Objects.nonNull(fallback)) {
                        publishers.replace(broker, this, fallback);
                        return;
                    }
                }
            } else {
                state = State.CLOSED;
                previous = null;
            }
            if (publishers.remove(broker, this) && publishers.isEmpty()) {
                BaseContext.replaceIfCurrent(MQEvent.MQ_EVENT_PUBLISHER, publishers, null);
            }
        }
    }

    /**
     * \u6cbf\u66ff\u6362\u94fe\u67e5\u627e\u80fd\u591f\u5728\u5f53\u524d\u6ce8\u518c\u5931\u8d25\u540e\u6062\u590d\u7684\u53d1\u5e03\u5668\u3002
     * @return \u4ecd\u53ef\u6062\u590d\u7684\u53d1\u5e03\u5668\uff0c\u65e0\u5019\u9009\u65f6\u8fd4\u56de\u7a7a
     */
    private Consumer<MQEvent> rollbackTarget() {
        Consumer<MQEvent> candidate = previous;
        while (candidate instanceof MQPublisherRegistration) {
            MQPublisherRegistration registration = (MQPublisherRegistration) candidate;
            if (registration.state == State.CLOSED) {
                return null;
            }
            if (registration.state != State.FAILED) {
                return candidate;
            }
            candidate = registration.previous;
        }
        return candidate;
    }

    /**
     * \u53d6\u5f97\u5171\u4eab\u6d88\u606f\u53d1\u5e03\u5668\u6620\u5c04\uff0c\u5c1a\u672a\u521b\u5efa\u65f6\u539f\u5b50\u521d\u59cb\u5316\u3002
     * @return \u7528\u4e8e\u767b\u8bb0\u6d88\u606f\u4ee3\u7406\u53d1\u5e03\u5668\u7684\u5171\u4eab\u6620\u5c04
     */
    private static Map<String, Consumer<MQEvent>> publisherMap() {
        Map<String, Consumer<MQEvent>> current = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
        while (Objects.isNull(current)) {
            Map<String, Consumer<MQEvent>> candidate = new ConcurrentHashMap<>();
            if (BaseContext.replaceIfCurrent(MQEvent.MQ_EVENT_PUBLISHER, null, candidate)) {
                return candidate;
            }
            current = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
        }
        return current;
    }
}
