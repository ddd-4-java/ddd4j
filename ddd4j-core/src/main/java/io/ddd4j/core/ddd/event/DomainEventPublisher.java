package io.ddd4j.core.ddd.event;

/** 领域事件发布端口，由各运行时适配层实现。 */
public interface DomainEventPublisher {
    <ID extends EntityId> void publish(DomainEvent<ID> event);

    /**
     * 发布任意对象（用于非 {@link DomainEvent} 体系的 MQ 事件等）。
     * 默认实现为 no-op，框架适配层可覆写以路由到本地事件总线。
     */
    default void publish(Object event) {
        // no-op
    }
}
