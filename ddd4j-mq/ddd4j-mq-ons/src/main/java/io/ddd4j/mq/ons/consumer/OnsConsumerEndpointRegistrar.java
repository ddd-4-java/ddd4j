package io.ddd4j.mq.ons.consumer;

import com.aliyun.openservices.ons.api.*;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.ons.ack.OnsMessageAcknowledgment;
import io.ddd4j.mq.ons.spi.OnsMQProperties;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQTagMatcher;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 阿里云 ONS 消费者端点注册器（编程式注册）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OnsConsumerEndpointRegistrar {

    private final OnsMQProperties properties;

    public OnsConsumerEndpointRegistrar(OnsMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        String group = Objects.isNull(definition.getGroup()) || io.ddd4j.kit.lang.StrKit.isBlank(definition.getGroup())
                ? properties.getConsumerId() : definition.getGroup();
        if (Objects.isNull(group)) {
            throw new IllegalStateException("OnsConsumerEndpointRegistrar requires consumerId or @MQEventListener(group=...)");
        }
        String topic = Objects.isNull(definition.getTopic()) ? properties.getTopic() : definition.getTopic();
        if (Objects.isNull(topic)) {
            throw new IllegalStateException("OnsConsumerEndpointRegistrar requires topic");
        }
        String tag = MQTagMatcher.findIncludes(definition.getTags()).stream().findFirst().orElse(null);
        Consumer consumer = ONSFactory.createConsumer(properties.sessionProperties(group));
        consumer.subscribe(topic, properties.subscriptionExpression(tag),
                (msg, ctx) -> handleMessage(msg, ctx, definition, handler));
        consumer.start();
    }

    private Action handleMessage(Message message, ConsumeContext context, MQListenerDefinition def,
                                 MQConsumerHandler handler) {
        try {
            if (!MQTagMatcher.match(message.getTag(), def.getTags())) {
                return Action.CommitMessage;
            }
            Map<String, Object> headers = new HashMap<>();
            headers.put(MQMessages.HEADER_DESTINATION_TOPIC, message.getTopic());
            if (Objects.nonNull(message.getTag())) {
                headers.put(MQMessages.HEADER_DESTINATION_TAG, message.getTag());
            }
            headers.put(OnsMessageAcknowledgment.HEADER_ONS_MESSAGE, message);
            headers.put(OnsMessageAcknowledgment.HEADER_ONS_CONTEXT, context);
            MQMessage<String> mq = MQMessage.of(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    headers,
                    message.getMsgID(),
                    message.getKey(),
                    message);
            OnsMessageAcknowledgment ack = new OnsMessageAcknowledgment(context, message);
            handler.handle(mq, ack);
            return ack.action();
        } catch (Exception ex) {
            return Action.ReconsumeLater;
        }
    }
}
