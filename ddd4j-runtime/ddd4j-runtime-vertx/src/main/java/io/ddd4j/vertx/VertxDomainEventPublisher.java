package io.ddd4j.vertx;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.fuin.ddd4j.core.EntityId;

import java.util.Objects;

/**
 * 将领域事件发布到 Vert.x 本地 EventBus。
 */
@Slf4j
public final class VertxDomainEventPublisher implements DomainEventPublisher {

    public static final String ADDRESS = "ddd4j.domain.events";
    private final Vertx vertx;

    public VertxDomainEventPublisher(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Vert.x event: {}", event.getClass().getName());
            vertx.eventBus().publish(ADDRESS, event);
        }
    }
}
