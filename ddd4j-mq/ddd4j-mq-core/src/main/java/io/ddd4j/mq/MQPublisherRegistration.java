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

    private enum State {
        PENDING, COMMITTED, FAILED, CLOSED
    }

    private final String broker;
    private final Consumer<MQEvent> delegate;
    private final Map<String, Consumer<MQEvent>> publishers;
    private Consumer<MQEvent> previous;
    private State state = State.PENDING;
    private boolean unregistered;

    private MQPublisherRegistration(String broker, Consumer<MQEvent> delegate,
                                    Map<String, Consumer<MQEvent>> publishers) {
        this.broker = broker;
        this.delegate = delegate;
        this.publishers = publishers;
    }

    static MQPublisherRegistration install(String broker, Consumer<MQEvent> delegate) {
        synchronized (REGISTRY_LOCK) {
            Map<String, Consumer<MQEvent>> publishers = publisherMap();
            MQPublisherRegistration registration = new MQPublisherRegistration(broker, delegate, publishers);
            registration.previous = publishers.put(broker, registration);
            return registration;
        }
    }

    @Override
    public void accept(MQEvent event) {
        delegate.accept(event);
    }

    void commit() {
        synchronized (REGISTRY_LOCK) {
            if (state == State.PENDING) {
                state = State.COMMITTED;
                previous = null;
            }
        }
    }

    void markFailed() {
        synchronized (REGISTRY_LOCK) {
            if (state == State.PENDING) {
                state = State.FAILED;
            }
        }
    }

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
