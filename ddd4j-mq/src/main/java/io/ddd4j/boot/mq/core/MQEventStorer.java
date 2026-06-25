package io.ddd4j.boot.mq.core;

import io.ddd4j.boot.core.contract.MQEvent;

public interface MQEventStorer<T extends MQEvent> {

    // 消息持久化
    void store(T mqEvent);
}