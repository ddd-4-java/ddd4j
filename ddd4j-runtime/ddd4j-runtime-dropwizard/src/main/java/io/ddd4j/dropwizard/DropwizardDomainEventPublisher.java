package io.ddd4j.dropwizard;

import java.util.Collections;
import java.util.ArrayList;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import lombok.extern.slf4j.Slf4j;
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
        this.listeners = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(listeners, "listeners must not be null")));
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
