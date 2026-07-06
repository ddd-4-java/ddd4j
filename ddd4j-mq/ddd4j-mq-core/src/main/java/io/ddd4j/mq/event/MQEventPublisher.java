package io.ddd4j.mq.event;

import io.ddd4j.mq.message.Destination;

/**
 * 领域事件发布端口。
 * <p>
 * 各 {@code ddd4j-cmpt-*} 模块通过 {@link io.ddd4j.mq.spi.BrokerAdapter} 提供实现。
 * </p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface MQEventPublisher {

    /**
     * 发布 MQ 事件（topic / tag / tenantId 已在 event 上设置完毕）。
     *
     * @param event 领域 MQ 事件
     */
    default void publish(MQEvent event) {
        publish(event, Destination.from(event));
    }

    /**
     * 发布领域事件到指定目的地。
     *
     * @param event       领域事件
     * @param destination 目的地（topic / tag / namespace）
     * @param <T>         事件类型
     */
    <T extends MQEvent> void publish(T event, Destination destination);

}
