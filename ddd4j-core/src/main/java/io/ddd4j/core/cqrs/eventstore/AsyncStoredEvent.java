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
package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EventId;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 响应式事件存储的持久化领域事件快照（ADR-0005）。
 *
 * <p>与同步 {@link StoredEvent} record <b>同包共居</b>的响应式轨道事件载体
 * （{@link AsyncEventStore} 的读写类型）：在 {@link DomainEvent} 完整因果元数据
 * （eventId／correlationId／causationId）之上，补充聚合定位
 * （aggregateType／aggregateId／version）与全局顺序。
 * 两者语义互补——同步轨道用最小 record，响应式轨道用富载体，勿混用。
 *
 * <p><b>全局 position</b></p>
 * <p>{@code position} 是跨所有聚合流全局递增的事件序号，由存储实现分配——这是 esc-api
 * 缺失而投影断线续传必需的基石能力（docs/reference/fuin-api-patterns/05-event-store.md）。
 * 与流内 {@code version}（聚合内递增）语义不同，勿混用。
 *
 * <p>{@code correlationId}／{@code causationId} 允许为 {@code null}（事件无因果关联时）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class AsyncStoredEvent {

    /** \u9886\u57df\u4e8b\u4ef6\u6807\u8bc6\u3002 */
    private final EventId eventId;
    /** \u7528\u4e8e\u533a\u5206\u805a\u5408\u7c7b\u578b\u7684\u903b\u8f91\u540d\u79f0\u3002 */
    private final String aggregateType;
    /** \u4e8b\u4ef6\u6240\u5c5e\u805a\u5408\u6839\u7684\u6807\u8bc6\u3002 */
    private final AggregateRootId aggregateId;
    /** \u4e8b\u4ef6\u6240\u5c5e\u805a\u5408\u6d41\u4e2d\u7684\u7248\u672c\u3002 */
    private final long version;
    /** \u4e8b\u4ef6\u5728\u5168\u5c40\u6d41\u4e2d\u7684\u4f4d\u7f6e\u3002 */
    private final long position;
    /** \u4e8b\u4ef6\u53d1\u751f\u65f6\u95f4\u3002 */
    private final ZonedDateTime timestamp;
    /** \u5b58\u50a8\u4e2d\u7684\u9886\u57df\u4e8b\u4ef6\u8d1f\u8f7d\u3002 */
    private final DomainEvent<?> payload;
    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final EventId correlationId;
    /** \u89e6\u53d1\u8be5\u9886\u57df\u4e8b\u4ef6\u4ea7\u751f\u7684\u4e0a\u6e38\u4e8b\u4ef6\u6807\u8bc6\u3002 */
    private final EventId causationId;

    /**
     * 创建持久化事件快照。
     *
     * @param eventId       事件标识
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @param version       聚合流内版本号
     * @param position      全局递增序号（由存储实现分配）
     * @param timestamp     事件时间戳
     * @param payload       类型化领域事件载荷
     * @param correlationId 关联事件标识；无关联时 {@code null}
     * @param causationId   因果事件标识；无因果时 {@code null}
     */
    public AsyncStoredEvent(EventId eventId, String aggregateType, AggregateRootId aggregateId,
                            long version, long position, ZonedDateTime timestamp,
                            DomainEvent<?> payload, EventId correlationId, EventId causationId) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        this.version = version;
        this.position = position;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.correlationId = correlationId;
        this.causationId = causationId;
    }

    /**
     * 返回事件标识。
     *
     * @return 事件标识
     */
    public EventId eventId() {
        return eventId;
    }

    /**
     * 返回聚合类型。
     *
     * @return 聚合类型
     */
    public String aggregateType() {
        return aggregateType;
    }

    /**
     * 返回聚合 ID。
     *
     * @return 聚合 ID
     */
    public AggregateRootId aggregateId() {
        return aggregateId;
    }

    /**
     * 返回聚合流内版本号。
     *
     * @return 版本号
     */
    public long version() {
        return version;
    }

    /**
     * 返回全局递增序号。
     *
     * @return 全局 position
     */
    public long position() {
        return position;
    }

    /**
     * 返回事件时间戳。
     *
     * @return 时间戳
     */
    public ZonedDateTime timestamp() {
        return timestamp;
    }

    /**
     * 返回类型化领域事件载荷。
     *
     * @return 领域事件
     */
    public DomainEvent<?> payload() {
        return payload;
    }

    /**
     * 返回关联事件标识。
     *
     * @return 关联事件标识；无关联时 {@code null}
     */
    public EventId correlationId() {
        return correlationId;
    }

    /**
     * 返回因果事件标识。
     *
     * @return 因果事件标识；无因果时 {@code null}
     */
    public EventId causationId() {
        return causationId;
    }
}
