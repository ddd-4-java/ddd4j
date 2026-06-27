package io.ddd4j.quarkus.core;

import io.ddd4j.core.contract.DomainEvent;
import io.ddd4j.core.contract.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

/**
 * Quarkus CDI 实现的领域事件发布者
 * <p>
 * 使用 CDI {@code Event<DomainEvent>} 发布领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
@Slf4j
@ApplicationScoped
public class CdiDomainEventPublisher implements DomainEventPublisher {

    @Inject
    Event<Object> event;

    @Override
    public void publish(DomainEvent domainEvent) {
        if (domainEvent == null) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}, aggregateId: {}",
                domainEvent.getClass().getSimpleName(), domainEvent.source());
        event.fire(domainEvent);
    }

    @Override
    public void publishAll(Collection<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        log.debug("Publishing {} domain events", events.size());
        for (DomainEvent event : events) {
            publish(event);
        }
    }
}
