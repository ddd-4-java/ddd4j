package io.ddd4j.sample.order.application;

/**
 * 已提交 Outbox 消息的传输端口。
 *
 * <p>Kafka、RocketMQ 或 HTTP 事件总线等基础设施实现本接口；应用层不依赖具体 broker。
 */
@FunctionalInterface
public interface IntegrationEventPublisher {

    /**
     * 发布一条 Outbox 消息。
     *
     * @param message 已提交、待发送的消息
     */
    void publish(OutboxMessage message);
}
