package io.ddd4j.mq.publish;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.mq.message.Destination;

/**
 * 领域事件发布端口。
 * <p>
 * 各 {@code ddd4j-cmpt-*} 模块通过 {@link io.ddd4j.mq.spi.BrokerAdapter} 提供实现。
 * </p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface EventPublisher extends io.ddd4j.core.event.EventPublisher {

    /**
     * 发布领域事件到指定目的地。
     *
     * @param event       领域事件
     * @param destination 目的地（topic / tag / namespace）
     * @param <T>         事件类型
     */
    <T extends MQEvent> void publish(T event, Destination destination);

    /**
     * 从事件字段推断目的地并发布（便捷方法）。
     *
     * @param event 领域事件
     */
    @Override
    default void publish(MQEvent event) {
        publish(event, Destination.from(event));
    }
}
