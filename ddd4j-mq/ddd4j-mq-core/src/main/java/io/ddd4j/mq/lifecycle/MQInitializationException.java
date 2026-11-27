/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq.lifecycle;

/**
 * 必选MQ能力初始化失败。
 */
public final class MQInitializationException extends IllegalStateException {

    /** Broker \u6807\u8bc6\u3002 */
    private final String broker;
    /** \u6d88\u606f\u4e3b\u9898\u3002 */
    private final String topic;
    /** \u6d88\u8d39\u7ec4\u3002 */
    private final String group;
    /** \u76d1\u542c\u65b9\u6cd5\u3002 */
    private final String listenerMethod;

    /**
     * \u521b\u5efa\u5fc5\u9700\u76d1\u542c\u5668\u521d\u59cb\u5316\u5931\u8d25\u5f02\u5e38\u3002
     * @param broker Broker \u6807\u8bc6
     * @param topic \u6d88\u606f\u4e3b\u9898
     * @param group \u6d88\u8d39\u7ec4
     * @param listenerMethod \u76d1\u542c\u65b9\u6cd5
     * @param cause \u539f\u59cb\u5f02\u5e38
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
}
