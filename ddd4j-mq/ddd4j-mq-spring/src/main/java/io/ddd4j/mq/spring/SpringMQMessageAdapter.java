package io.ddd4j.mq.spring;

import io.ddd4j.mq.contract.MQMessage;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Messaging ↔ ddd4j-mq-core 转换器
 * <p>
 * 在 ddd4j-mq-spring 模块中提供，便于 broker 适配层复用。
 *
 * @author wandl
 * @since 3.4.x
 */
public final class SpringMQMessageAdapter {

    private SpringMQMessageAdapter() {
    }

    /**
     * ddd4j MQMessage → Spring Messaging Message
     */
    public static <T> Message<T> toSpringMessage(MQMessage<T> mqMessage) {
        if (mqMessage == null) {
            return null;
        }
        Map<String, Object> headers = new HashMap<>(mqMessage.getHeaders());
        return MessageBuilder.createMessage(mqMessage.getPayload(), new MessageHeaders(headers));
    }

    /**
     * Spring Messaging Message → ddd4j MQMessage
     */
    @SuppressWarnings("unchecked")
    public static <T> MQMessage<T> fromSpringMessage(Message<T> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> headers = new HashMap<>(message.getHeaders());
        return MQMessage.of(headers, message.getPayload());
    }
}
