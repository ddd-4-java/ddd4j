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
package io.ddd4j.data.event.store.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 事件存储 JPA 实体——映射统一 schema {@code DDD4J_EVENT_STORE}。
 *
 * <p>主键为 {@code (aggregate_id, version)} 复合键，{@code position} 全局单调递增且唯一。
 * 与 R2DBC 模块共用同一张表，保证跨运行时数据一致性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Entity
@Table(name = "DDD4J_EVENT_STORE")
@IdClass(StoredEventEntityId.class)
public class StoredEventEntity {

    @Id
    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Id
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "position", nullable = false, unique = true)
    private long position;

    @Column(name = "event_type", nullable = false, length = 512)
    private String eventType;

    @Column(name = "event_id", length = 64)
    private String eventId;

    /**
     * 事件载荷 JSON 文本。使用 CLOB 以支持大体积事件。
     */
    @Column(name = "payload", nullable = false, columnDefinition = "CLOB")
    private String payload;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    public StoredEventEntity() {
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
