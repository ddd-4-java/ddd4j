package io.ddd4j.guice.event;

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Objects;

/**
 * Guice 实现的领域事件发布者
 * <p>
 * 使用 Google Guava {@link EventBus} 发布进程内领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class GuiceDomainEventPublisher implements DomainEventPublisher {

    /** Guava 事件总线 */
    private final EventBus eventBus;

    @Inject
    public GuiceDomainEventPublisher(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public <T> void publish(DomainEvent<T> event) {
        if (Objects.isNull(event)) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
        eventBus.post(event);
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
