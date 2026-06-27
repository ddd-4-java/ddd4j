package io.ddd4j.core.api.contract;

import java.io.Serializable;
import java.time.Instant;

/**
 * 领域事件接口（纯 Java，不继承 Spring ApplicationEvent）
 * <p>
 * 各框架适配层提供事件发布机制：
 * <ul>
 *   <li>Spring: 通过 ApplicationEventPublisher 发布</li>
 *   <li>Quarkus: 通过 CDI Event&lt;T&gt; 发布</li>
 *   <li>Javalin/Guice: 通过 Guava EventBus 发布</li>
 * </ul>
 *
 * @author wandl
 */
public interface DomainEvent extends Serializable {

    /**
     * 获取事件 ID
     *
     * @return 事件唯一标识
     */
    String getEventId();

    /**
     * 获取事件类型
     *
     * @return 事件类型名称
     */
    String getEventType();

    /**
     * 获取事件发生时间
     *
     * @return 事件发生时间
     */
    Instant getOccurredOn();

    /**
     * 获取关联的聚合根 ID
     *
     * @return 聚合根 ID
     */
    String getAggregateId();

    /**
     * 获取租户 ID（多租户场景）
     *
     * @return 租户 ID
     */
    default String getTenantId() {
        return null;
    }
}
