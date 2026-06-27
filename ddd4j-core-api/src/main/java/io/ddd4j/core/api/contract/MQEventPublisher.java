package io.ddd4j.core.api.contract;

/**
 * MQ 事件发布端口（契约层）。
 * <p>
 * 由 {@code ddd4j-mq} 或 {@code ddd4j-cmpt-*} 模块提供 Bean 实现；
 * 各框架适配层提供实现：
 * <ul>
 *   <li>Spring: 通过 ApplicationContext.getBeansOfType(MQEventPublisher.class) 获取</li>
 *   <li>Quarkus: 通过 CDI BeanManager 获取所有实现</li>
 *   <li>Javalin/Guice: 通过 Injector 获取所有实现</li>
 * </ul>
 *
 * @see io.ddd4j.mq.spi.MQBrokerAdapter
 */
public interface MQEventPublisher {

    /**
     * 发布 MQ 事件（topic / tag / tenantId 已在 event 上设置完毕）。
     *
     * @param event 领域 MQ 事件
     */
    void publish(MQEvent event);
}
