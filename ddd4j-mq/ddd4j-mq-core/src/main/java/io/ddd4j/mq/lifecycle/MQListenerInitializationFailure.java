/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import java.util.Objects;

/** MQ监听器初始化失败的安全诊断信息。 */
public final class MQListenerInitializationFailure {

    private final String broker;
    private final String topic;
    private final String group;
    private final String listenerMethod;
    private final boolean required;
    private final String reason;

    public MQListenerInitializationFailure(String broker, String topic, String group,
                                           String listenerMethod, boolean required, String reason) {
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.listenerMethod = Objects.requireNonNull(listenerMethod, "listenerMethod must not be null");
        this.required = required;
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public String broker() { return broker; }
    public String topic() { return topic; }
    public String group() { return group; }
    public String listenerMethod() { return listenerMethod; }
    public boolean required() { return required; }
    public String reason() { return reason; }
    public String getBroker() { return broker; }
    public String getTopic() { return topic; }
    public String getGroup() { return group; }
    public String getListenerMethod() { return listenerMethod; }
    public boolean isRequired() { return required; }
    public String getReason() { return reason; }
}
