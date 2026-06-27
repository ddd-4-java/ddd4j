package io.ddd4j.core.contract;

import java.util.Collection;

/**
 * 领域事件发布者接口（纯 Java）
 * <p>
 * 各框架适配层提供实现：
 * <ul>
 *   <li>Spring: 基于 ApplicationEventPublisher</li>
 *   <li>Quarkus: 基于 CDI Event&lt;DomainEvent&gt;</li>
 *   <li>Javalin/Guice: 基于 Guava EventBus</li>
 * </ul>
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 */
public interface DomainEventPublisher {

    /**
     * 发布领域事件
     *
     * @param event 领域事件
     */
    void publish(DomainEvent event);

    /**
     * 批量发布领域事件
     *
     * @param events 领域事件集合
     */
    default void publishAll(Collection<DomainEvent> events) {
        if (events != null) {
            events.forEach(this::publish);
        }
    }
}
