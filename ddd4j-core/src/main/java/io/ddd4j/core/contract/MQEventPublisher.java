package io.ddd4j.core.contract;

/**
 * MQ 事件发布端口（契约层）。
 * <p>
 * 由 {@code ddd4j-mq} 或 {@code ddd4j-cmpt-*} 模块提供 Bean 实现；
 * {@link MQEvent#publish()} 委托本接口发布。
 * </p>
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
