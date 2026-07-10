package io.ddd4j.core.ddd.event;

import org.fuin.ddd4j.core.EntityId;

import java.util.Collection;
import java.util.Objects;

/**
 * 领域事件发布者接口（纯 Java）
 * <p>
 * 各框架适配层提供实现：
 * <ul>
 *   <li>Spring: 基于 ApplicationEventPublisher</li>
 *   <li>Quarkus: 基于 CDI Event&lt;DomainEvent&gt;</li>
 *   <li>Guice: 基于 Guava EventBus</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface DomainEventPublisher {

    /**
     * 发布领域事件
     *
     * @param event 领域事件
     */
    <ID extends EntityId> void publish(DomainEvent<ID> event);

    /**
     * 发布任意对象（用于非 {@link DomainEvent} 体系的 MQ 事件等）。
     *
     * <p>默认实现为 no-op，框架适配层可覆写以路由到本地事件总线
     * （如 Spring {@code ApplicationEventPublisher}、CDI {@code Event}、Guava {@code EventBus}）。
     *
     * @param event 任意事件对象
     */
    default void publish(Object event) {
        // no-op
    }

    /**
     * 批量发布领域事件
     *
     * @param events 领域事件集合
     */
    default <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> events) {
        if (Objects.nonNull(events)) {
            events.forEach(this::publish);
        }
    }

}
