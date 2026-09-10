/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

/** 必选MQ能力初始化失败。 */
public final class MQInitializationException extends IllegalStateException {

    private final String broker;
    private final String topic;
    private final String group;
    private final String listenerMethod;

    public MQInitializationException(String broker, String topic, String group,
                                     String listenerMethod, Throwable cause) {
        super("Required MQ listener initialization failed: broker=" + broker
                + ", topic=" + topic + ", group=" + group + ", listener=" + listenerMethod, cause);
        this.broker = broker;
        this.topic = topic;
        this.group = group;
        this.listenerMethod = listenerMethod;
    }

    public String broker() { return broker; }
    public String topic() { return topic; }
    public String group() { return group; }
    public String listenerMethod() { return listenerMethod; }
    public String getBroker() { return broker; }
    public String getTopic() { return topic; }
    public String getGroup() { return group; }
    public String getListenerMethod() { return listenerMethod; }
}
