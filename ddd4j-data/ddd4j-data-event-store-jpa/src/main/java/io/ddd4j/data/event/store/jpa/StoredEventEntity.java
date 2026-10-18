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

import io.ddd4j.core.constant.EventStoreConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 事件存储 JPA 实体——映射统一 schema {@code DDD4J_EVENT_STORE}。
 *
 * <p>主键为 {@code (aggregate_type, aggregate_id, version)} 复合键，
 * {@code position} 全局单调递增且唯一。
 * 与 R2DBC 模块共用同一张表，保证跨运行时数据一致性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Entity
@Table(name = EventStoreConstants.TABLE_NAME, uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_store_position", columnNames = EventStoreConstants.COLUMN_POSITION)
})
@IdClass(StoredEventEntityId.class)
public class StoredEventEntity {

    @Id
    @Column(name = EventStoreConstants.COLUMN_AGGREGATE_TYPE, nullable = false, length = 255)
    private String aggregateType;

    @Id
    @Column(name = EventStoreConstants.COLUMN_AGGREGATE_ID, nullable = false, length = 255)
    private String aggregateId;

    @Id
    @Column(name = EventStoreConstants.COLUMN_VERSION, nullable = false)
    private long version;

    @Column(name = EventStoreConstants.COLUMN_POSITION, nullable = false)
    private long position;

    @Column(name = EventStoreConstants.COLUMN_EVENT_TYPE, nullable = false, length = 512)
    private String eventType;

    @Column(name = EventStoreConstants.COLUMN_EVENT_ID, length = 64)
    private String eventId;

    @Column(name = EventStoreConstants.COLUMN_CORRELATION_ID, length = 64)
    private String correlationId;

    @Column(name = EventStoreConstants.COLUMN_CAUSATION_ID, length = 64)
    private String causationId;

    /**
     * 事件载荷 JSON 文本。使用可与 JDBI/R2DBC 共享的 SQL TEXT 类型。
     */
    @Column(name = EventStoreConstants.COLUMN_PAYLOAD, nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = EventStoreConstants.COLUMN_TIMESTAMP, nullable = false)
    private Instant timestamp;

    public StoredEventEntity() {
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 设置值。
     * @param aggregateType 字符串参数。
     */
    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    /**
     * 设置值。
     * @param aggregateId 字符串参数。
     */
    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    /**
     * 获取值。
     * @return 结果数值。
     */
    public long getVersion() {
        return version;
    }

    /**
     * 设置值。
     * @param version version 参数。
     */
    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * 获取值。
     * @return 结果数值。
     */
    public long getPosition() {
        return position;
    }

    /**
     * 设置值。
     * @param position position 参数。
     */
    public void setPosition(long position) {
        this.position = position;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 设置值。
     * @param eventType 字符串参数。
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * 设置值。
     * @param eventId 字符串参数。
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * 设置值。
     * @param correlationId 字符串参数。
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getCausationId() {
        return causationId;
    }

    /**
     * 设置值。
     * @param causationId 字符串参数。
     */
    public void setCausationId(String causationId) {
        this.causationId = causationId;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 设置值。
     * @param payload 消息载荷。
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * 获取值。
     * @return 方法执行结果。
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * 设置值。
     * @param timestamp timestamp 参数。
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
