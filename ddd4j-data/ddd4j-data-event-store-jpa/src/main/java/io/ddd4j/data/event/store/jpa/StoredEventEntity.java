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

    /** \u7528\u4e8e\u533a\u5206\u805a\u5408\u7c7b\u578b\u7684\u903b\u8f91\u540d\u79f0\u3002 */
    @Id
    @Column(name = EventStoreConstants.COLUMN_AGGREGATE_TYPE, nullable = false, length = 255)
    private String aggregateType;

    /** \u4e8b\u4ef6\u6240\u5c5e\u805a\u5408\u6839\u7684\u6807\u8bc6\u3002 */
    @Id
    @Column(name = EventStoreConstants.COLUMN_AGGREGATE_ID, nullable = false, length = 255)
    private String aggregateId;

    /** \u5f53\u524d\u4e8b\u4ef6\u5728\u6240\u5c5e\u805a\u5408\u6d41\u4e2d\u7684\u7248\u672c\u3002 */
    @Id
    @Column(name = EventStoreConstants.COLUMN_VERSION, nullable = false)
    private long version;

    /** \u5f53\u524d\u4e8b\u4ef6\u5728\u4e8b\u4ef6\u8868\u5168\u5c40\u8bfb\u53d6\u987a\u5e8f\u4e2d\u7684\u4f4d\u7f6e\u3002 */
    @Column(name = EventStoreConstants.COLUMN_POSITION, nullable = false)
    private long position;

    /** \u7528\u4e8e\u6062\u590d\u5b9e\u9645\u8d1f\u8f7d Java \u7c7b\u578b\u7684\u4e8b\u4ef6\u7c7b\u578b\u540d\u3002 */
    @Column(name = EventStoreConstants.COLUMN_EVENT_TYPE, nullable = false, length = 512)
    private String eventType;

    /** \u9886\u57df\u4e8b\u4ef6\u6807\u8bc6\u3002 */
    @Column(name = EventStoreConstants.COLUMN_EVENT_ID, length = 64)
    private String eventId;

    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    @Column(name = EventStoreConstants.COLUMN_CORRELATION_ID, length = 64)
    private String correlationId;

    /** \u89e6\u53d1\u8be5\u9886\u57df\u4e8b\u4ef6\u4ea7\u751f\u7684\u4e0a\u6e38\u4e8b\u4ef6\u6807\u8bc6\u3002 */
    @Column(name = EventStoreConstants.COLUMN_CAUSATION_ID, length = 64)
    private String causationId;

    /**
     * 事件载荷 JSON 文本。使用可与 JDBI/R2DBC 共享的 SQL TEXT 类型。
     */
    @Column(name = EventStoreConstants.COLUMN_PAYLOAD, nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** \u5f53\u524d\u4e8b\u4ef6\u53d1\u751f\u7684\u65f6\u95f4\u3002 */
    @Column(name = EventStoreConstants.COLUMN_TIMESTAMP, nullable = false)
    private Instant timestamp;

    /**
     * 创建空的StoredEventEntity实例，供 JPA 或序列化框架使用。
     */
    public StoredEventEntity() {
    }

    /**
     * 返回聚合标识。
     *
     * @return 聚合标识
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * 返回聚合类型。
     *
     * @return 聚合类型
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 设置聚合类型。
     *
     * @param aggregateType 聚合类型
     */
    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    /**
     * 设置聚合标识。
     *
     * @param aggregateId 聚合标识
     */
    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    /**
     * 返回事件版本。
     *
     * @return 事件版本
     */
    public long getVersion() {
        return version;
    }

    /**
     * 设置事件版本。
     *
     * @param version 事件版本
     */
    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * 返回全局位置。
     *
     * @return 全局位置
     */
    public long getPosition() {
        return position;
    }

    /**
     * 设置全局位置。
     *
     * @param position 全局位置
     */
    public void setPosition(long position) {
        this.position = position;
    }

    /**
     * 返回事件类型。
     *
     * @return 事件类型
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 设置事件类型。
     *
     * @param eventType 事件类型
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * 返回事件标识。
     *
     * @return 事件标识
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * 设置事件标识。
     *
     * @param eventId 事件标识
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * 返回关联标识。
     *
     * @return 关联标识
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * 设置关联标识。
     *
     * @param correlationId 关联标识
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * 返回因果标识。
     *
     * @return 因果标识
     */
    public String getCausationId() {
        return causationId;
    }

    /**
     * 设置因果标识。
     *
     * @param causationId 因果标识
     */
    public void setCausationId(String causationId) {
        this.causationId = causationId;
    }

    /**
     * 返回事件载荷。
     *
     * @return 事件载荷
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 设置事件载荷。
     *
     * @param payload 事件载荷
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * 返回事件时间。
     *
     * @return 事件时间
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * 设置事件时间。
     *
     * @param timestamp 事件时间
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
