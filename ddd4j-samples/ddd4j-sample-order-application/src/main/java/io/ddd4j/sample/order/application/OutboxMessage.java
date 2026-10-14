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
package io.ddd4j.sample.order.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.time.Instant;

/** 等待可靠发布的订单集成事件消息。 */
public final class OutboxMessage {

    private static final long serialVersionUID = 0L;

    private final String id;

    private final String aggregateId;

    private final String eventType;

    private final Object payload;

    private final Instant occurredAt;

    /**
     * @param id 消息标识
     * @param aggregateId 订单聚合标识
     * @param eventType 事件类型
     * @param payload 事件内容
     * @param occurredAt 事件发生时间
     */
    @JsonCreator()
    public OutboxMessage(@JsonProperty("id") String id, @JsonProperty("aggregateId") String aggregateId, @JsonProperty("eventType") String eventType, @JsonProperty("payload") Object payload, @JsonProperty("occurredAt") Instant occurredAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    /** @return 消息标识 */
    @JsonProperty("id")
    public String id() {
        return id;
    }

    /** @return 订单聚合标识 */
    @JsonProperty("aggregateId")
    public String aggregateId() {
        return aggregateId;
    }

    /** @return 事件类型 */
    @JsonProperty("eventType")
    public String eventType() {
        return eventType;
    }

    /** @return 事件内容 */
    @JsonProperty("payload")
    public Object payload() {
        return payload;
    }

    /** @return 事件发生时间 */
    @JsonProperty("occurredAt")
    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        OutboxMessage other = (OutboxMessage) obj;
        return Objects.equals(this.id, other.id) && Objects.equals(this.aggregateId, other.aggregateId) && Objects.equals(this.eventType, other.eventType) && Objects.equals(this.payload, other.payload) && Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(aggregateId);
        result = 31 * result + Objects.hashCode(eventType);
        result = 31 * result + Objects.hashCode(payload);
        result = 31 * result + Objects.hashCode(occurredAt);
        return result;
    }

    @Override
    public String toString() {
        return "OutboxMessage[id=" + id + ", aggregateId=" + aggregateId + ", eventType=" + eventType + ", payload=" + payload + ", occurredAt=" + occurredAt + "]";
    }
}
