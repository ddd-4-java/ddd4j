package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EventId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** 持久化领域事件快照，包含流定位、全局位置和因果元数据。 */
public final class StoredEvent {
    private final EventId eventId; private final String aggregateType; private final AggregateRootId aggregateId;
    private final long version; private final long position; private final ZonedDateTime timestamp;
    private final DomainEvent<?> payload; private final EventId correlationId; private final EventId causationId;
    public StoredEvent(EventId eventId, String aggregateType, AggregateRootId aggregateId, long version, long position,
                       ZonedDateTime timestamp, DomainEvent<?> payload, EventId correlationId, EventId causationId) {
        this.eventId = Objects.requireNonNull(eventId, "eventId"); this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId"); this.version = version; this.position = position;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp"); this.payload = Objects.requireNonNull(payload, "payload");
        this.correlationId = correlationId; this.causationId = causationId;
    }
    public EventId eventId() { return eventId; } public String aggregateType() { return aggregateType; }
    public AggregateRootId aggregateId() { return aggregateId; } public long version() { return version; }
    public long position() { return position; } public ZonedDateTime timestamp() { return timestamp; }
    public DomainEvent<?> payload() { return payload; } public EventId correlationId() { return correlationId; }
    public EventId causationId() { return causationId; }
}
