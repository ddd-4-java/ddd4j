/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

import java.util.Objects;

/**
 * MQ监听器初始化失败的安全诊断信息。
 */
public final class MQListenerInitializationFailure {

    private final String broker;
    private final String topic;
    private final String group;
    private final String listenerMethod;
    private final boolean required;
    private final String reason;

    /**
     * 保存监听器初始化失败的诊断信息。
     *
     * @param broker Broker 标识
     * @param topic 消息主题
     * @param group 消费组
     * @param listenerMethod 监听方法
     * @param required 是否为必需监听器
     * @param reason 失败原因
     */
    public MQListenerInitializationFailure(String broker, String topic, String group,
                                           String listenerMethod, boolean required, String reason) {
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.listenerMethod = Objects.requireNonNull(listenerMethod, "listenerMethod must not be null");
        this.required = required;
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    /**
     * 返回Broker 标识。
     *
     * @return Broker 标识
     */
    public String broker() {
        return broker;
    }

    /**
     * 返回消息主题。
     *
     * @return 消息主题
     */
    public String topic() {
        return topic;
    }

    /**
     * 返回消费组。
     *
     * @return 消费组
     */
    public String group() {
        return group;
    }

    /**
     * 返回监听方法。
     *
     * @return 监听方法
     */
    public String listenerMethod() {
        return listenerMethod;
    }

    /**
     * 返回是否必须启动。
     *
     * @return 是否必须启动
     */
    public boolean required() {
        return required;
    }

    /**
     * 返回失败原因。
     *
     * @return 失败原因
     */
    public String reason() {
        return reason;
    }

    /**
     * 返回Broker 标识。
     *
     * @return Broker 标识
     */
    public String getBroker() {
        return broker;
    }

    /**
     * 返回消息主题。
     *
     * @return 消息主题
     */
    public String getTopic() {
        return topic;
    }

    /**
     * 返回消费组。
     *
     * @return 消费组
     */
    public String getGroup() {
        return group;
    }

    /**
     * 返回监听方法。
     *
     * @return 监听方法
     */
    public String getListenerMethod() {
        return listenerMethod;
    }

    /**
     * 返回是否必须启动。
     *
     * @return 是否必须启动
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 返回失败原因。
     *
     * @return 失败原因
     */
    public String getReason() {
        return reason;
    }
}
