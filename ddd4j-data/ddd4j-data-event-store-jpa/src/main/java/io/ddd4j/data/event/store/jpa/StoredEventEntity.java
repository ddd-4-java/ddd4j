package io.ddd4j.data.eventstore.jpa;

import io.ddd4j.core.cqrs.eventstore.EventStoreConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 事件存储 JPA 实体——映射统一 schema {@code DDD4J_EVENT_STORE}。
 *
 * <p>主键为 {@code (aggregate_type, aggregate_id, version)} 复合键，
 * {@code position} 全局单调递增且唯一；{@code payload} 为跨方言 TEXT 类型
 * （与 2.0.x/3.0.x 的 TEXT 统一 schema 对齐）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
@Entity(name = "StoredEventEntity")
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

    @Column(name = EventStoreConstants.COLUMN_PAYLOAD, nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = EventStoreConstants.COLUMN_TIMESTAMP, nullable = false)
    private Instant timestamp;

    public String getAggregateType() { return aggregateType; }

    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }

    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public long getVersion() { return version; }

    public void setVersion(long version) { this.version = version; }

    public long getPosition() { return position; }

    public void setPosition(long position) { this.position = position; }

    public String getEventType() { return eventType; }

    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventId() { return eventId; }

    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getCorrelationId() { return correlationId; }

    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getCausationId() { return causationId; }

    public void setCausationId(String causationId) { this.causationId = causationId; }

    public String getPayload() { return payload; }

    public void setPayload(String payload) { this.payload = payload; }

    public Instant getTimestamp() { return timestamp; }

    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
