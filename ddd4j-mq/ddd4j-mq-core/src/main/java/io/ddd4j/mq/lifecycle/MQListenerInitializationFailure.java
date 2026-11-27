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

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u7684\u6ce8\u518c\u540d\u79f0\u3002 */
    private final String broker;
    /** \u6d88\u606f\u6295\u9012\u6216\u8ba2\u9605\u7684\u4e3b\u9898\u3002 */
    private final String topic;
    /** \u521d\u59cb\u5316\u5931\u8d25\u76d1\u542c\u5668\u6240\u5c5e\u7684\u6d88\u8d39\u8005\u7ec4\u3002 */
    private final String group;
    /** \u7528\u4e8e\u5b9a\u4f4d\u521d\u59cb\u5316\u5931\u8d25\u7684\u76d1\u542c\u65b9\u6cd5\u6807\u8bc6\u3002 */
    private final String listenerMethod;
    /** \u542f\u52a8\u6d88\u606f\u7ec4\u4ef6\u5931\u8d25\u65f6\u662f\u5426\u963b\u6b62\u5e94\u7528\u5c31\u7eea\u3002 */
    private final boolean required;
    /** \u53ef\u62a5\u544a\u7684\u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u539f\u56e0\u3002 */
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
