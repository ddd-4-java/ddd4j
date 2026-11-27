/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.mq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 标识一次发布器注册，并维护当前启动失败时可恢复的前驱。
 */
final class MQPublisherRegistration implements Consumer<MQEvent> {

    /**
     * 串行化 MQ 客户端的启动上下文和发布器注册操作。
     */
    static final Object REGISTRY_LOCK = new Object();
    /**
     * 当前消息代理的注册名称。
     */
    private final String broker;
    /**
     * 实际向对应消息代理发布事件的回调。
     */
    private final Consumer<MQEvent> delegate;
    /**
     * DDD4J 共享上下文中的代理发布器映射。
     */
    private final Map<String, Consumer<MQEvent>> publishers;
    /**
     * 当前注册覆盖前同一代理使用的发布器。
     */
    private Consumer<MQEvent> previous;
    /**
     * 记录当前启动或注册流程所处的状态。
     */
    private State state = State.PENDING;
    /**
     * 标记当前注册是否已经执行注销。
     */
    private boolean unregistered;
    /**
     * 建立当前代理发布器与共享注册表之间的注册关系。
     *
     * @param broker     代理注册键
     * @param delegate   发送回调
     * @param publishers 共享注册表
     */
    private MQPublisherRegistration(String broker, Consumer<MQEvent> delegate,
                                    Map<String, Consumer<MQEvent>> publishers) {
        this.broker = broker;
        this.delegate = delegate;
        this.publishers = publishers;
    }

    /**
     * 在共享注册表中为代理安装发布器，并记录安装前的值。
     *
     * @param broker   代理注册键
     * @param delegate 发送回调
     * @return 可提交、标记失败并注销的发布器注册
     */
    static MQPublisherRegistration install(String broker, Consumer<MQEvent> delegate) {
        synchronized (REGISTRY_LOCK) {
            Map<String, Consumer<MQEvent>> publishers = publisherMap();
            MQPublisherRegistration registration = new MQPublisherRegistration(broker, delegate, publishers);
            registration.previous = publishers.put(broker, registration);
            return registration;
        }
    }

    /**
     * 取得共享消息发布器映射，尚未创建时原子初始化。
     *
     * @return 用于登记消息代理发布器的共享映射
     */
    private static Map<String, Consumer<MQEvent>> publisherMap() {
        Map<String, Consumer<MQEvent>> current = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
        while (Objects.isNull(current)) {
            Map<String, Consumer<MQEvent>> candidate = new ConcurrentHashMap<>();
            if (BaseContext.replaceIfCurrent(MQEvent.MQ_EVENT_PUBLISHER, null, candidate)) {
                return candidate;
            }
            current = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
        }
        return current;
    }

    /**
     * 接收调用方提交的记录。
     *
     * <p>此实现调用 <code>delegate.accept(event)</code>。</p>
     *
     * @param event 当前发布、消费或处理的事件
     */
    @Override
    public void accept(MQEvent event) {
        delegate.accept(event);
    }

    /**
     * 提交仍待完成的发布器注册并释放旧值引用。
     */
    void commit() {
        synchronized (REGISTRY_LOCK) {
            if (state == State.PENDING) {
                state = State.COMMITTED;
                previous = null;
            }
        }
    }

    /**
     * 将尚未提交的发布器注册标记为失败。
     */
    void markFailed() {
        synchronized (REGISTRY_LOCK) {
            if (state == State.PENDING) {
                state = State.FAILED;
            }
        }
    }

    /**
     * 注销当前注册，注册失败时尝试恢复仍然可用的旧发布器。
     */
    void unregister() {
        synchronized (REGISTRY_LOCK) {
            if (unregistered) {
                return;
            }
            unregistered = true;
            if (state == State.FAILED) {
                if (BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER) == publishers) {
                    Consumer<MQEvent> fallback = rollbackTarget();
                    if (Objects.nonNull(fallback)) {
                        publishers.replace(broker, this, fallback);
                        return;
                    }
                }
            } else {
                state = State.CLOSED;
                previous = null;
            }
            if (publishers.remove(broker, this) && publishers.isEmpty()) {
                BaseContext.replaceIfCurrent(MQEvent.MQ_EVENT_PUBLISHER, publishers, null);
            }
        }
    }

    /**
     * 沿替换链查找能够在当前注册失败后恢复的发布器。
     *
     * @return 仍可恢复的发布器，无候选时返回空
     */
    private Consumer<MQEvent> rollbackTarget() {
        Consumer<MQEvent> candidate = previous;
        while (candidate instanceof MQPublisherRegistration) {
            MQPublisherRegistration registration = (MQPublisherRegistration) candidate;
            if (registration.state == State.CLOSED) {
                return null;
            }
            if (registration.state != State.FAILED) {
                return candidate;
            }
            candidate = registration.previous;
        }
        return candidate;
    }

    /**
     * 跟踪消息发布器注册从待提交到提交、失败或关闭的阶段。
     */
    private enum State {
        /**
         * 尚未提交的发布器注册。
         */
        PENDING,
        /**
         * 注册已成功提交，可供业务使用。
         */
        COMMITTED,
        /**
         * 初始化未完成，注销时尝试恢复旧发布器。
         */
        FAILED,
        /**
         * 注册已关闭且不再作为回滚候选。
         */
        CLOSED
    }
}
