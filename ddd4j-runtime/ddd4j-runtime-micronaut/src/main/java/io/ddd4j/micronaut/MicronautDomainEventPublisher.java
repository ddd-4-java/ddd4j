package io.ddd4j.micronaut;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.micronaut.context.event.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import java.util.Objects;

/**
 * 基于 Micronaut ApplicationEventPublisher 的进程内领域事件发布器。
 */
@Slf4j
public final class MicronautDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher<Object> publisher;

    public MicronautDomainEventPublisher(ApplicationEventPublisher<Object> publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Micronaut event: {}", event.getClass().getName());
            publisher.publishEvent(event);
        }
    }
}
