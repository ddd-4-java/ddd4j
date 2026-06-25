package io.ddd4j.boot.mq.consume;

import io.ddd4j.boot.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.boot.mq.contract.MQMessage;

/**
 * MQ 消费处理函数，由 Broker Adapter 在注册端点时绑定。
 */
@FunctionalInterface
public interface MQConsumerHandler {

    /**
     * 处理单条消息。
     *
     * @param message 消息信封
     * @param ack     确认端口
     * @throws Exception 业务异常时由上层决定重试或 DLQ
     */
    void handle(MQMessage<?> message, MessageAcknowledgment ack) throws Exception;
}
