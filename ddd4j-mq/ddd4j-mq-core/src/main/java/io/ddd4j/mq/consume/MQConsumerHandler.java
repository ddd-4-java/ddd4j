package io.ddd4j.mq.consume;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.contract.MQMessage;
import org.springframework.messaging.Message;

/**
 * MQ 消费处理函数，由 Broker Adapter 在注册端点时绑定。
 */
@FunctionalInterface
public interface MQConsumerHandler {

    /**
     * 处理单条消息。
     *
     * @param message 消息信封（{@link Message}）
     * @param ack     确认端口
     * @throws Exception 业务异常时由上层决定重试或 DLQ
     */
    void handle(Message<?> message, MessageAcknowledgment ack) throws Exception;

    /**
     * 处理单条消息（兼容旧 {@link MQMessage} 参数）。
     * <p>
     * 默认实现将 {@link MQMessage} 转换为 {@link Message} 后委托给主方法。
     *
     * @param message 消息信封（{@link MQMessage}，兼容旧 API）
     * @param ack     确认端口
     * @throws Exception 业务异常时由上层决定重试或 DLQ
     */
    default void handle(MQMessage<?> message, MessageAcknowledgment ack) throws Exception {
        handle(message.toMessage(), ack);
    }
}
