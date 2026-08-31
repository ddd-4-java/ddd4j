package io.ddd4j.core.ddd.event;

/** 无运行时事件总线时使用的无操作发布端口。 */
public final class NoopDomainEventPublisher implements DomainEventPublisher {
    public static final NoopDomainEventPublisher INSTANCE = new NoopDomainEventPublisher();
    private NoopDomainEventPublisher() { }
    @Override public <ID extends EntityId> void publish(DomainEvent<ID> event) { }
}
