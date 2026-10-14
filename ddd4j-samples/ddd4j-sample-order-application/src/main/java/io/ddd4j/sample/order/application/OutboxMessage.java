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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.Instant;

/**
 * Outbox 消息，Java 8 等价实现保留 record 值语义。
 */
@Value
public class OutboxMessage {
String id;
    String aggregateId;
    String eventType;
    Object payload;
    Instant occurredAt;

    /** Creates an outbox message.
     * @param id message identifier
     * @param aggregateId aggregate identifier
     * @param eventType event type
     * @param payload event payload
     * @param occurredAt occurrence time
     */
    @JsonCreator
    public OutboxMessage(@JsonProperty("id") String id,
                         @JsonProperty("aggregateId") String aggregateId,
                         @JsonProperty("eventType") String eventType,
                         @JsonProperty("payload") Object payload,
                         @JsonProperty("occurredAt") Instant occurredAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public String id() {
        return id;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public Object payload() {
        return payload;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
