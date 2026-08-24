package io.ddd4j.data.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EventId;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 持久化的领域事件快照（ADR-0005）。
 *
 * <p>事件从 {@link EventStore} 读回后的载体：在 {@link DomainEvent} 完整因果元数据
 * （eventId／correlationId／causationId）之上，补充聚合定位（aggregateType／aggregateId／version）
 * 与全局顺序。
 *
 * <h3>全局 position</h3>
 * <p>{@code position} 是跨所有聚合流全局递增的事件序号，由存储实现分配——这是 esc-api
 * 缺失而投影断线续传必需的基石能力（docs/reference/fuin-api-patterns/05-event-store.md）。
 * 与流内 {@code version}（聚合内递增）语义不同，勿混用。
 *
 * <p>{@code correlationId}／{@code causationId} 允许为 {@code null}（事件无因果关联时）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class StoredEvent {

    private final EventId eventId;
    private final String aggregateType;
    private final AggregateRootId aggregateId;
    private final long version;
    private final long position;
    private final ZonedDateTime timestamp;
    private final DomainEvent<?> payload;
    private final EventId correlationId;
    private final EventId causationId;

    /**
     * 创建持久化事件快照。
     *
     * @param eventId        事件标识
     * @param aggregateType  聚合类型
     * @param aggregateId    聚合 ID
     * @param version        聚合流内版本号
     * @param position       全局递增序号（由存储实现分配）
     * @param timestamp      事件时间戳
     * @param payload        类型化领域事件载荷
     * @param correlationId  关联事件标识；无关联时 {@code null}
     * @param causationId    因果事件标识；无因果时 {@code null}
     */
    public StoredEvent(EventId eventId, String aggregateType, AggregateRootId aggregateId,
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
