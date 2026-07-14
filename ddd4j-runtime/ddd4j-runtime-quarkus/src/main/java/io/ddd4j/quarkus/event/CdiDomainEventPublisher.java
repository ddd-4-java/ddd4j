package io.ddd4j.quarkus.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.fuin.ddd4j.core.EntityId;

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
@DefaultBean
@ApplicationScoped
public class CdiDomainEventPublisher implements DomainEventPublisher {

    /**
     * CDI 事件总线
     */
    @Inject
    Event<Object> event;

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> domainEvent) {
        if (Objects.isNull(domainEvent)) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}", domainEvent.getClass().getSimpleName());
        event.fire(domainEvent);
    }
}
