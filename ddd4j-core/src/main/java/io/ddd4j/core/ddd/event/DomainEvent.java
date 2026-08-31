package io.ddd4j.core.ddd.event;

import io.ddd4j.core.context.BaseContext;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 无框架领域事件基类，承载事件溯源所需的标识、时间、路径、版本和因果关联。
 *
 * @param <ID> 事件源实体标识类型
 */
public abstract class DomainEvent<ID extends EntityId> implements Event {
    private static final long serialVersionUID = 1L;
    private final EventId eventId;
    private final ZonedDateTime eventTimestamp;
    private EventId correlationId;
    private EventId causationId;
    private EntityIdPath entityIdPath;
    private AggregateVersion aggregateVersion;

    protected DomainEvent() {
        this.eventId = new EventId();
        this.eventTimestamp = ZonedDateTime.now();
    }
    protected DomainEvent(EntityIdPath entityIdPath) {
        this();
        this.entityIdPath = Objects.requireNonNull(entityIdPath, "entityIdPath must not be null");
    }
    protected DomainEvent(EntityIdPath entityIdPath, Event causingEvent) {
        this(entityIdPath);
        Event actual = Objects.requireNonNull(causingEvent, "causingEvent must not be null");
        this.correlationId = actual.getCorrelationId() == null ? actual.getEventId() : actual.getCorrelationId();
        this.causationId = actual.getEventId();
    }
    @Override public EventId getEventId() { return eventId; }
    @Override public EventType getEventType() { return new EventType(getClass().getSimpleName()); }
    @Override public ZonedDateTime getEventTimestamp() { return eventTimestamp; }
    @Override public EventId getCorrelationId() { return correlationId; }
    @Override public EventId getCausationId() { return causationId; }
    public EntityIdPath getEntityIdPath() { return entityIdPath; }
    @SuppressWarnings("unchecked") public ID getEntityId() { return entityIdPath == null ? null : (ID) entityIdPath.last(); }
    public AggregateVersion getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(AggregateVersion aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    /** 通过框架适配层注册的纯 Java 发布端口发布当前事件。 */
    public void publish() {
        DomainEventPublisher publisher = BaseContext.get(DomainEventPublisher.class);
        if (publisher == null) throw new IllegalStateException("DomainEventPublisher is not registered");
        publisher.publish(this);
    }
}
