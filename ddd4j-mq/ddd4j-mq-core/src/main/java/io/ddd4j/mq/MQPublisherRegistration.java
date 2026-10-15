/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq;

import io.ddd4j.mq.event.MQEvent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 标识一次 MQ publisher 注册，避免复用同一个发布函数时混淆生命周期所有权。
 */
final class MQPublisherRegistration implements Consumer<MQEvent> {

    private final Consumer<MQEvent> delegate;
    private volatile boolean active = true;

    MQPublisherRegistration(Consumer<MQEvent> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void accept(MQEvent event) {
        delegate.accept(event);
    }

    boolean isActive() {
        return active;
    }

    void deactivate() {
        active = false;
    }
}
