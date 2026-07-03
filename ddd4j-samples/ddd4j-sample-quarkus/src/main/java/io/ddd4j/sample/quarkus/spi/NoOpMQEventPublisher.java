package io.ddd4j.sample.quarkus.spi;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.core.event.MQEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * MQ 事件发布者：No-Op 示例实现。
 *
 * <p>作为 CDI {@link ApplicationScoped} Bean，ddd4j-runtime-quarkus 启动期会扫描
 * 所有 {@link MQEventPublisher} 实现并注入到 {@code BaseContext}。
 *
 * <p>真实应用应注入 {@code KafkaMQEventPublisher} / {@code RabbitMQEventPublisher} 等实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class NoOpMQEventPublisher implements MQEventPublisher {

    @Override
    public void publish(MQEvent event) {
        if (event == null) {
            return;
        }
        System.out.println("[MQEvent] " + event.getClass().getSimpleName());
    }
}