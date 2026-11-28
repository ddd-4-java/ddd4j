/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.mq.delivery;

import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 将 Inbox 去重和消费者业务处理串联的协调器。
 *
 * <p>调用此类的方法与业务处理器必须由同一个事务拦截器包裹：首次记录和业务写入一起提交，
 * 处理器抛异常时一起回滚，重复消息则不再执行业务处理但仍应由调用方 ACK。
 */
@Slf4j
public final class MQInboxProcessor {

    /** \u68c0\u67e5\u5e76\u8bb0\u5f55\u6d88\u606f\u6d88\u8d39\u5904\u7406\u72b6\u6001\u7684\u6536\u4ef6\u7bb1\u5b58\u50a8\u3002 */
    private final MQInboxStore store;
    /** \u6b64\u6536\u4ef6\u7bb1\u5904\u7406\u5668\u6240\u5c5e\u7684\u903b\u8f91\u6d88\u8d39\u8005\u6807\u8bc6\u3002 */
    private final String consumerId;
    /** \u63a5\u6536\u6d88\u606f\u6295\u9012\u6216\u8fd0\u884c\u72b6\u6001\u53d8\u5316\u7684\u56de\u8c03\u3002 */
    private final MQDeliveryObserver observer;

    /**
     * \u521b\u5efa\u4f7f\u7528\u9ed8\u8ba4\u89c2\u5bdf\u5668\u7684 Inbox \u5904\u7406\u5668\u3002
     * @param store Inbox \u6301\u4e45\u5316\u7aef\u53e3
     * @param consumerId \u7a33\u5b9a\u6d88\u8d39\u8005\u6807\u8bc6
     */
    public MQInboxProcessor(MQInboxStore store, String consumerId) {
        this(store, consumerId, NoopMQDeliveryObserver.INSTANCE);
    }

    /**
     * 创建带投递结果观察器的 Inbox 处理器。
     *
     * @param store      Inbox 持久化端口
     * @param consumerId 稳定消费者标识
     * @param observer   旁路观测实现
     */
    public MQInboxProcessor(MQInboxStore store, String consumerId, MQDeliveryObserver observer) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        if (StrKit.isBlank(consumerId)) {
            throw new IllegalArgumentException("consumerId must not be blank");
        }
        this.consumerId = consumerId;
        this.observer = Objects.requireNonNull(observer, "observer must not be null");
    }

    /**
     * 处理一条可靠消息。
     *
     * @param messageId   生产端稳定消息标识
     * @param processedAt 当前处理时间
     * @param handler     业务处理器
     * @return {@code true} 表示首次处理，{@code false} 表示重复消息
     */
    public boolean process(String messageId, Instant processedAt, Runnable handler) {
        if (StrKit.isBlank(messageId)) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        Objects.requireNonNull(processedAt, "processedAt must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        try {
            if (!store.recordIfAbsent(consumerId, messageId, processedAt)) {
                notifyObserver(deliveryObserver -> deliveryObserver.onInboxDuplicate(consumerId, messageId));
                return false;
            }
            handler.run();
            notifyObserver(deliveryObserver -> deliveryObserver.onInboxProcessed(consumerId, messageId));
            return true;
        } catch (RuntimeException exception) {
            notifyObserver(deliveryObserver -> deliveryObserver.onInboxFailed(consumerId, messageId));
            throw exception;
        }
    }

    /**
     * \u901a\u77e5\u6295\u9012\u89c2\u5bdf\u5668\u5e76\u8bb0\u5f55\u5176\u8fd0\u884c\u65f6\u5f02\u5e38\uff0c\u907f\u514d\u5f71\u54cd\u5b9e\u9645\u6295\u9012\u8def\u5f84\u3002
     * @param action \u8981\u5728\u5f53\u524d\u89c2\u5bdf\u5668\u4e0a\u8c03\u7528\u7684\u901a\u77e5\u52a8\u4f5c
     */
    private void notifyObserver(Consumer<MQDeliveryObserver> action) {
        try {
            action.accept(observer);
        } catch (RuntimeException exception) {
            log.warn("MQ delivery observer failed and was ignored", exception);
        }
    }
}
