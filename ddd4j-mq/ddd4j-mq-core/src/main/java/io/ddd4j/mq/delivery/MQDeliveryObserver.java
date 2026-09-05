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

/**
 * 可靠消息投递结果观察端口。
 *
 * <p>实现仅用于日志、指标和审计等旁路观测，不得改变 Outbox 或 Inbox 的业务结果。
 * 回调不携带负载、消息头和异常文本，避免观察实现意外记录敏感数据。
 */
public interface MQDeliveryObserver {

    /**
     * Outbox 消息已由持有租约的实例确认发布。
     *
     * @param record 已发布记录
     */
    default void onOutboxPublished(MQOutboxRecord record) {
    }

    /**
     * Outbox 消息已安排后续重试。
     *
     * @param record 投递失败记录
     */
    default void onOutboxRetry(MQOutboxRecord record) {
    }

    /**
     * Outbox 消息已超过重试上限并进入死信状态。
     *
     * @param record 死信记录
     */
    default void onOutboxDead(MQOutboxRecord record) {
    }

    /**
     * Outbox 发送或状态确认失败，最终状态尚未确定。
     *
     * @param record 投递状态不确定的记录
     */
    default void onOutboxFailed(MQOutboxRecord record) {
    }

    /**
     * Inbox 首次成功处理消息。
     *
     * @param consumerId 消费者标识
     * @param messageId 稳定消息标识
     */
    default void onInboxProcessed(String consumerId, String messageId) {
    }

    /**
     * Inbox 识别到已处理的重复消息。
     *
     * @param consumerId 消费者标识
     * @param messageId 稳定消息标识
     */
    default void onInboxDuplicate(String consumerId, String messageId) {
    }

    /**
     * Inbox 记录或业务处理失败，调用方应保持不 ACK 以触发重投。
     *
     * @param consumerId 消费者标识
     * @param messageId 稳定消息标识
     */
    default void onInboxFailed(String consumerId, String messageId) {
    }
}
