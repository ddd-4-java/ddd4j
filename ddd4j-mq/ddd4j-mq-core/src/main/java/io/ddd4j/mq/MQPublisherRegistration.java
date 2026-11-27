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

    /** \u5b9e\u9645\u5411\u5bf9\u5e94\u6d88\u606f\u4ee3\u7406\u53d1\u5e03\u4e8b\u4ef6\u7684\u56de\u8c03\u3002 */
    private final Consumer<MQEvent> delegate;
    /** 保存 <code>true</code>，供 <code>MQPublisherRegistration</code> 的处理流程复用。 */
    private volatile boolean active = true;

    /**
     * 初始化 <code>MQPublisherRegistration</code> 使用的 <code>delegate</code>。
     *
     * @param delegate \u53d1\u9001\u56de\u8c03
     */
    MQPublisherRegistration(Consumer<MQEvent> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** {@inheritDoc} */
    @Override
    public void accept(MQEvent event) {
        delegate.accept(event);
    }

    /**
     * 读取<code>active</code> 的当前值。
     *
     * @return 返回表达式 <code>active</code>
     */
    boolean isActive() {
        return active;
    }

    /** 执行 <code>deactivate</code> 对应的处理。 */
    void deactivate() {
        active = false;
    }
}
