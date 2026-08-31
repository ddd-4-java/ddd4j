package io.ddd4j.data.eventstore.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZonedDateTime;

/**
 * 事件存储 JPA 实体：以追加写（append-only）方式落地 {@code io.ddd4j.data.eventstore.StoredEvent}。
 *
 * <p>设计要点（ADR-0005，见 {@code docs/adr/0005-event-store-spi.md}）：
 * <ul>
 *   <li>{@code position} 为代理主键（IDENTITY 自增），即事件存储的全局流位置；仅暴露 getter，
 *       由数据库生成，禁止业务侧改写。</li>
 *   <li>{@code (aggregate_type, aggregate_id, version)} 唯一约束（{@code uk_aggregate_version}）
 *       是乐观并发控制的数据层兜底：同一聚合重复追加同一版本号将违反约束，
 *       配合 SPI 层 {@code AggregateVersionConflictException} 语义。</li>
 *   <li>{@code payload} 为序列化后的事件负载（JSON），由 SPI 层的
 *       {@code EventPayloadSerializer} 负责多态序列化/反序列化。</li>
 *   <li>{@code correlationId}／{@code causationId}／{@code tenantId} 为可空追踪维度。</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see io.ddd4j.data.eventstore.StoredEvent
 * @since 2.0.x
 */
@Entity
@Table(name = "ddd4j_stored_event",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_aggregate_version",
           columnNames = {"aggregate_type", "aggregate_id", "version"}))
public class StoredEventEntity {

    /** 全局流位置：数据库自增主键，事件存储的追加顺序号。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long position;

    /** 事件 ID（UUID 字符串，36 字符）。 */
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    /** 聚合类型名（限定名，最长 128 字符）。 */
    @Column(name = "aggregate_type", nullable = false, length = 128)
    private String aggregateType;

    /** 聚合 ID 字符串形式（最长 128 字符）。 */
    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    /** 聚合版本号（乐观并发控制维度，从 1 起）。 */
    @Column(name = "version", nullable = false)
    private Long version;

    /** 事件类型名（限定名，最长 256 字符）。 */
    @Column(name = "event_type", nullable = false, length = 256)
    private String eventType;

    /** 序列化事件负载（可与 JDBI/R2DBC 共享的 JSON TEXT）。 */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** 关联 ID（可选，36 字符）。 */
    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    /** 因果 ID（可选，36 字符）。 */
    @Column(name = "causation_id", length = 36)
    private String causationId;

    /** 租户 ID（可选，64 字符）。 */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /** 事件发生时间（带时区）。 */
    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    public Long getPosition() { return position; }

    public String getEventId() { return eventId; }

    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getAggregateType() { return aggregateType; }

    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }

    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public Long getVersion() { return version; }

    public void setVersion(Long version) { this.version = version; }

    public String getEventType() { return eventType; }

    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }

    public void setPayload(String payload) { this.payload = payload; }

    public String getCorrelationId() { return correlationId; }

    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getCausationId() { return causationId; }

    public void setCausationId(String causationId) { this.causationId = causationId; }

    public String getTenantId() { return tenantId; }

    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public ZonedDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
}
