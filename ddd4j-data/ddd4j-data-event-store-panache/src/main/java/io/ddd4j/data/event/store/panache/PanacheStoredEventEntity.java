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
package io.ddd4j.data.event.store.panache;

import io.ddd4j.core.constant.EventStoreConstants;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 事件存储 Quarkus Panache 实体——映射统一 schema {@code DDD4J_EVENT_STORE}。
 *
 * <p>与 {@code ddd4j-data-event-store-jpa} 的 {@code StoredEventEntity} 共享同一张表
 * （{@value io.ddd4j.core.constant.EventStoreConstants#TABLE_NAME}），
 * 保证跨运行时数据一致性。
 *
 * <p><b>公有字段风格是 Panache 的刻意约定</b>：Quarkus Panache active record 模式
 * （继承 {@link PanacheEntityBase}）以公有字段直接承载列映射，省去 getter/setter 样板。
 *
 * <h3>主键设计</h3>
 * <p>复合主键 {@code (aggregate_type, aggregate_id, version)} 通过 {@link IdClass} 实现，
 * 保证同一聚合内的版本号唯一。{@code position} 为全局单调递增列（带唯一约束），
 * 由应用层分配（{@link #nextPosition}），用于全局分页读取。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see PanacheEventStore
 * @since 3.0.0
 */
@Entity
@Table(name = EventStoreConstants.TABLE_NAME, uniqueConstraints = @UniqueConstraint(
        name = "uk_position", columnNames = {EventStoreConstants.COLUMN_POSITION}))
@IdClass(PanacheStoredEventId.class)
public class PanacheStoredEventEntity extends PanacheEntityBase {

    /** 聚合类型（复合主键之一）。 */
    @Id
    @Column(name = EventStoreConstants.COLUMN_AGGREGATE_TYPE, nullable = false, length = 255)
    public String aggregateType;

    /** 聚合根标识（复合主键之一）。 */
    @Id
    @Column(name = EventStoreConstants.COLUMN_AGGREGATE_ID, nullable = false, length = 255)
    public String aggregateId;

    /** 聚合内版本号（复合主键之一，从 0 起递增）。 */
    @Id
    @Column(name = EventStoreConstants.COLUMN_VERSION, nullable = false)
    public long version;

    /** 全局流位置（应用层分配，唯一约束）。 */
    @Column(name = EventStoreConstants.COLUMN_POSITION, nullable = false, unique = true)
    public long position;

    /** 事件类型全限定名（用于反序列化还原）。 */
    @Column(name = EventStoreConstants.COLUMN_EVENT_TYPE, nullable = false, length = 512)
    public String eventType;

    /** 事件 ID（可选）。 */
    @Column(name = EventStoreConstants.COLUMN_EVENT_ID, length = 64)
    public String eventId;

    /** 序列化事件载荷（JSON 文本，CLOB 以支持大体积事件）。 */
    @Lob
    @Column(name = EventStoreConstants.COLUMN_PAYLOAD, nullable = false)
    public String payload;

    /** 关联事件标识（可选）。 */
    @Column(name = EventStoreConstants.COLUMN_CORRELATION_ID, length = 64)
    public String correlationId;

    /** 因果事件标识（可选）。 */
    @Column(name = EventStoreConstants.COLUMN_CAUSATION_ID, length = 64)
    public String causationId;

    /** 事件存储时间。 */
    @Column(name = EventStoreConstants.COLUMN_TIMESTAMP, nullable = false)
    public Instant timestamp;

}
