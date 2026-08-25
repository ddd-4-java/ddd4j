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
package io.ddd4j.mq.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.io.Serial;

/**
 * 领域事件跨进程载体。
 *
 * <p>将 core {@link io.ddd4j.core.ddd.event.DomainEvent} 序列化为 JSON 字符串后，
 * 作为 {@code payload} 字段附在本 MQ 事件上发送到 MQ Broker。
 * 消费端收到后通过 {@link #payload} 反序列化还原为具体的领域事件子类。
 *
 * <h3>消息结构</h3>
 * <ul>
 *   <li>{@link #getTopic()}：事件类型名称（如 {@code "OrderCreatedEvent"}）</li>
 *   <li>{@link #getTag()}：固定 {@code "domain-event"}</li>
 *   <li>{@link #getDomainEventType()}：领域事件完全限定类名，消费端据此反序列化</li>
 *   <li>{@link #getPayload()}：领域事件 JSON 序列化结果</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Getter
public class DomainEventCarrier extends MQEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 领域事件完全限定类名，消费端据此选择反序列化目标类型。
     */
    private final String domainEventType;

    /**
     * 领域事件 JSON 序列化结果。
     */
    private final String payload;

    /**
     * 构造领域事件载体。
     *
     * @param domainEventType 领域事件完全限定类名
     * @param payload         领域事件 JSON 序列化结果
     */
    @JsonCreator
    public DomainEventCarrier(@JsonProperty("domainEventType") String domainEventType,
                              @JsonProperty("payload") String payload) {
        this.domainEventType = domainEventType;
        this.payload = payload;
    }
}
