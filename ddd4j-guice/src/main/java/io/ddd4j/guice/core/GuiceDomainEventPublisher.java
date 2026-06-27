package io.ddd4j.guice.core;

import com.google.common.eventbus.EventBus;
import io.ddd4j.core.contract.DomainEvent;
import io.ddd4j.core.contract.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

/**
 * Javalin/Guice 实现的领域事件发布者
 * <p>
 * 使用 Google Guava {@link EventBus} 发布进程内领域事件。
 *
 * @author Loong Wan
 * @since 3.4.x
 */
@Slf4j
public class GuiceDomainEventPublisher implements DomainEventPublisher {

    private final EventBus eventBus;

    public GuiceDomainEventPublisher(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
        eventBus.post(event);
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
