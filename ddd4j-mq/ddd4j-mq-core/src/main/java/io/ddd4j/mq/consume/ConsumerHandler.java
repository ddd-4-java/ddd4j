package io.ddd4j.mq.consume;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.message.Message;

/**
 * MQ 消费处理函数（纯 Java，零 Spring 依赖）。
 *
 * <p>由 Broker Adapter 在注册端点时绑定。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@FunctionalInterface
public interface ConsumerHandler {

    /**
     * 处理单条消息。
     *
     * @param message 消息信封（{@link Message}，纯 Java 模型）
     * @param ack     确认端口
     * @throws Exception 业务异常时由上层决定重试或 DLQ
     */
    void handle(Message<?> message, Acknowledgment ack) throws Exception;
}
