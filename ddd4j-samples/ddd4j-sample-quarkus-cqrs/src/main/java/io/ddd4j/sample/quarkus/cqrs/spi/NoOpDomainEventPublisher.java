package io.ddd4j.sample.quarkus.cqrs.spi;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.fuin.ddd4j.core.EntityId;

import java.util.Collection;
import java.util.Objects;

/**
 * Quarkus CQRS 示例使用的进程内领域事件发布器。
 */
@Slf4j
@ApplicationScoped
public class NoOpDomainEventPublisher implements DomainEventPublisher {

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        if (Objects.isNull(event)) {
            return;
        }
        log.info("[DomainEvent] {} -> {}", event.getClass().getSimpleName(), event.source());
    }

    @Override
    public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> events) {
        if (Objects.nonNull(events)) {
            events.forEach(this::publish);
        }
    }
}
