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
package io.ddd4j.extension.otel;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.delivery.MQDeliveryObserver;
import io.ddd4j.mq.delivery.MQOutboxRecord;

/**
 * 将可靠消息投递结果映射为 OpenTelemetry 指标的观察器。
 *
 * <p>Broker 名称由装配层提供，指标仅写入受控结果和 broker 标签；消息 ID、目的地、负载、
 * Header 与异常内容均不会进入指标属性。
 */
public final class OtelMQDeliveryObserver implements MQDeliveryObserver {

    /**
     * 用于区分消息投递指标来源的代理名称。
     */
    private final String broker;

    /**
     * @param broker 低基数 broker 类型，例如 kafka 或 rabbitmq
     */
    public OtelMQDeliveryObserver(String broker) {
        this.broker = StrKit.isBlank(broker) ? "unknown" : StrKit.trim(broker);
    }

    /**
     * Outbox 消息已由持有租约的实例确认发布。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.outboxPublished(broker)</code>。</p>
     *
     * @param record 已发布记录
     */
    @Override
    public void onOutboxPublished(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxPublished(broker);
    }

    /**
     * Outbox 消息已安排后续重试。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.outboxRetry(broker)</code>。</p>
     *
     * @param record 投递失败记录
     */
    @Override
    public void onOutboxRetry(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxRetry(broker);
    }

    /**
     * Outbox 消息已超过重试上限并进入死信状态。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.outboxDead(broker)</code>。</p>
     *
     * @param record 死信记录
     */
    @Override
    public void onOutboxDead(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxDead(broker);
    }

    /**
     * Outbox 发送或状态确认失败，最终状态尚未确定。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.outboxFailed(broker)</code>。</p>
     *
     * @param record 投递状态不确定的记录
     */
    @Override
    public void onOutboxFailed(MQOutboxRecord record) {
        MqDeliveryMetrics.outboxFailed(broker);
    }

    /**
     * Inbox 首次成功处理消息。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.inboxProcessed(broker)</code>。</p>
     *
     * @param consumerId 消费者标识
     * @param messageId  稳定消息标识
     */
    @Override
    public void onInboxProcessed(String consumerId, String messageId) {
        MqDeliveryMetrics.inboxProcessed(broker);
    }

    /**
     * Inbox 识别到已处理的重复消息。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.inboxDuplicate(broker)</code>。</p>
     *
     * @param consumerId 消费者标识
     * @param messageId  稳定消息标识
     */
    @Override
    public void onInboxDuplicate(String consumerId, String messageId) {
        MqDeliveryMetrics.inboxDuplicate(broker);
    }

    /**
     * Inbox 记录或业务处理失败，调用方应保持不 ACK 以触发重投。
     *
     * <p>此实现调用 <code>MqDeliveryMetrics.inboxFailed(broker)</code>。</p>
     *
     * @param consumerId 消费者标识
     * @param messageId  稳定消息标识
     */
    @Override
    public void onInboxFailed(String consumerId, String messageId) {
        MqDeliveryMetrics.inboxFailed(broker);
    }
}
