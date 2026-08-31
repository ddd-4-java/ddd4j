package io.ddd4j.core.ddd.event;

/** 领域事件发布端口，由各运行时适配层实现。 */
public interface DomainEventPublisher {
    <ID extends EntityId> void publish(DomainEvent<ID> event);
}
