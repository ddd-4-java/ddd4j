package io.ddd4j.sample.javalin.cqrs.spi;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.core.event.MQEventPublisher;

/**
 * MQ 事件发布者：No-Op 示例实现。
 *
 * <p>真实应用应注入 {@code KafkaMQEventPublisher} / {@code RabbitMQEventPublisher} 等实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class NoOpMQEventPublisher implements MQEventPublisher {

    @Override
    public void publish(MQEvent event) {
        System.out.println("[MQEvent] " + event.getClass().getSimpleName());
    }
}