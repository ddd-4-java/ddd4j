/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

/**
 * 必选MQ能力初始化失败。
 */
public final class MQInitializationException extends IllegalStateException {

    /** Broker 标识。 */
    private final String broker;
    /** 消息主题。 */
    private final String topic;
    /** 消费组。 */
    private final String group;
    /** 监听方法。 */
    private final String listenerMethod;

    /**
     * 创建必需监听器初始化失败异常。
     *
     * @param broker Broker 标识
     * @param topic 消息主题
     * @param group 消费组
     * @param listenerMethod 监听方法
     * @param cause 原始异常
     */
    public MQInitializationException(String broker, String topic, String group,
                                     String listenerMethod, Throwable cause) {
        super("Required MQ listener initialization failed: broker=" + broker
                + ", topic=" + topic + ", group=" + group + ", listener=" + listenerMethod, cause);
        this.broker = broker;
        this.topic = topic;
        this.group = group;
        this.listenerMethod = listenerMethod;
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
}
