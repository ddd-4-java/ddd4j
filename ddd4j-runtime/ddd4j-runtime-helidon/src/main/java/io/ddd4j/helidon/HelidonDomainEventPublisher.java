package io.ddd4j.helidon;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import jakarta.enterprise.inject.spi.BeanManager;
import lombok.extern.slf4j.Slf4j;
import java.util.Objects;

/**
 * 基于 Helidon MP CDI 事件总线的领域事件发布器。
 */
@Slf4j
public final class HelidonDomainEventPublisher implements DomainEventPublisher {

    private final BeanManager beanManager;

    public HelidonDomainEventPublisher(BeanManager beanManager) {
        this.beanManager = Objects.requireNonNull(beanManager, "beanManager must not be null");
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Helidon CDI event: {}", event.getClass().getName());
            beanManager.getEvent().fire(event);
        }
    }
}
