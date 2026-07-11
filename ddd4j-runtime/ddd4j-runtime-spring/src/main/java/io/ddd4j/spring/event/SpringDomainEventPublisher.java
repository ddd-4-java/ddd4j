package io.ddd4j.spring.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.fuin.ddd4j.core.EntityId;

import java.util.Objects;

/**
 * Spring 实现的领域事件发布者
 * <p>
 * 使用 Spring ApplicationEventPublisher 发布领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    /**
     * Spring 应用事件发布器
     */
    private final ApplicationEventPublisher publisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        log.debug("Initializing SpringDomainEventPublisher");
        Objects.requireNonNull(publisher, "ApplicationEventPublisher cannot be null");
        this.publisher = publisher;
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        if (Objects.isNull(event)) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
        publisher.publishEvent(event);
    }
}
