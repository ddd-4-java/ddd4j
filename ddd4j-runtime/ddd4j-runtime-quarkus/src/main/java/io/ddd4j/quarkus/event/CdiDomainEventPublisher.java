package io.ddd4j.quarkus.event;

import io.ddd4j.core.domain.event.DomainEvent;
import io.ddd4j.core.domain.event.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Objects;

/**
 * Quarkus CDI 实现的领域事件发布者
 * <p>
 * 使用 CDI {@code Event<DomainEvent>} 发布领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
public class CdiDomainEventPublisher implements DomainEventPublisher {

    @Inject
    Event<Object> event;

    @Override
    public <T> void publish(DomainEvent<T> domainEvent) {
        if (Objects.isNull(domainEvent)) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}, aggregateId: {}",
                domainEvent.getClass().getSimpleName(), domainEvent.source());
        event.fire(domainEvent);
    }

    @Override
    public <T> void publishAll(Collection<DomainEvent<T>> events) {
        if (Objects.isNull(events) || events.isEmpty()) {
            return;
        }
        log.debug("Publishing {} domain events", events.size());
        for (DomainEvent<T> event : events) {
            publish(event);
        }
    }
}
