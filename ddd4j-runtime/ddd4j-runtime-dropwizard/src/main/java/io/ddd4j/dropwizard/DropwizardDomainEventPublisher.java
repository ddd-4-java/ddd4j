package io.ddd4j.dropwizard;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.fuin.ddd4j.core.EntityId;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Dropwizard 显式监听器领域事件发布器。
 */
@Slf4j
public final class DropwizardDomainEventPublisher implements DomainEventPublisher {

    private final List<Consumer<Object>> listeners;

    public DropwizardDomainEventPublisher(Collection<Consumer<Object>> listeners) {
        this.listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners must not be null"));
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Dropwizard event: {}", event.getClass().getName());
            listeners.forEach(listener -> listener.accept(event));
        }
    }
}
