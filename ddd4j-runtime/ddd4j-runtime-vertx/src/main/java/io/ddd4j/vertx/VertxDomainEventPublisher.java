package io.ddd4j.vertx;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将领域事件发布到 Vert.x 本地 EventBus。
 */
@Slf4j
public final class VertxDomainEventPublisher implements DomainEventPublisher {

    public static final String ADDRESS = "ddd4j.domain.events";
    private static final String CODEC_PREFIX = "ddd4j.local-event.";
    private final Vertx vertx;
    private final Map<Class<?>, String> codecNames = new ConcurrentHashMap<>();

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
            String codecName = codecNames.computeIfAbsent(event.getClass(), this::registerLocalCodec);
            vertx.eventBus().publish(ADDRESS, event, new DeliveryOptions().setCodecName(codecName));
        }
    }

    private String registerLocalCodec(Class<?> eventType) {
        String codecName = CODEC_PREFIX + eventType.getName();
        vertx.eventBus().registerCodec(new LocalEventMessageCodec(codecName));
        return codecName;
    }
}
