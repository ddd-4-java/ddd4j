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
     * \u4fdd\u5b58\u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u7684\u8bca\u65ad\u4fe1\u606f\u3002
     * @param broker Broker \u6807\u8bc6
     * @param topic \u6d88\u606f\u4e3b\u9898
     * @param group \u6d88\u8d39\u7ec4
     * @param listenerMethod \u76d1\u542c\u65b9\u6cd5
     * @param required \u662f\u5426\u4e3a\u5fc5\u9700\u76d1\u542c\u5668
     * @param reason \u5931\u8d25\u539f\u56e0
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
     * \u8fd4\u56deBroker \u6807\u8bc6\u3002
     * @return Broker \u6807\u8bc6
     */
    public String broker() {
        return broker;
    }

    /**
     * \u8fd4\u56de\u6d88\u606f\u4e3b\u9898\u3002
     * @return \u6d88\u606f\u4e3b\u9898
     */
    public String topic() {
        return topic;
    }

    /**
     * \u8fd4\u56de\u6d88\u8d39\u7ec4\u3002
     * @return \u6d88\u8d39\u7ec4
     */
    public String group() {
        return group;
    }

    /**
     * \u8fd4\u56de\u76d1\u542c\u65b9\u6cd5\u3002
     * @return \u76d1\u542c\u65b9\u6cd5
     */
    public String listenerMethod() {
        return listenerMethod;
    }

    /**
     * \u8fd4\u56de\u662f\u5426\u5fc5\u987b\u542f\u52a8\u3002
     * @return \u662f\u5426\u5fc5\u987b\u542f\u52a8
     */
    public boolean required() {
        return required;
    }

    /**
     * \u8fd4\u56de\u5931\u8d25\u539f\u56e0\u3002
     * @return \u5931\u8d25\u539f\u56e0
     */
    public String reason() {
        return reason;
    }

    /**
     * \u8fd4\u56deBroker \u6807\u8bc6\u3002
     * @return Broker \u6807\u8bc6
     */
    public String getBroker() {
        return broker;
    }

    /**
     * \u8fd4\u56de\u6d88\u606f\u4e3b\u9898\u3002
     * @return \u6d88\u606f\u4e3b\u9898
     */
    public String getTopic() {
        return topic;
    }

    /**
     * \u8fd4\u56de\u6d88\u8d39\u7ec4\u3002
     * @return \u6d88\u8d39\u7ec4
     */
    public String getGroup() {
        return group;
    }

    /**
     * \u8fd4\u56de\u76d1\u542c\u65b9\u6cd5\u3002
     * @return \u76d1\u542c\u65b9\u6cd5
     */
    public String getListenerMethod() {
        return listenerMethod;
    }

    /**
     * \u8fd4\u56de\u662f\u5426\u5fc5\u987b\u542f\u52a8\u3002
     * @return \u662f\u5426\u5fc5\u987b\u542f\u52a8
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * \u8fd4\u56de\u5931\u8d25\u539f\u56e0\u3002
     * @return \u5931\u8d25\u539f\u56e0
     */
    public String getReason() {
        return reason;
    }
}
