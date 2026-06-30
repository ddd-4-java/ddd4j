package io.ddd4j.mq.ons.consumer;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.Consumer;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.ONSFactory;
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
 * Programmatic Alibaba ONS consumer registrar.
 */
public class OnsConsumerEndpointRegistrar {

    private final OnsMQProperties properties;

    public OnsConsumerEndpointRegistrar(OnsMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        String group = java.util.Objects.isNull(definition.getGroup()) || io.ddd4j.kit.lang.StrKit.isBlank(definition.getGroup())
                ? properties.getConsumerId() : definition.getGroup();
        if (java.util.Objects.isNull(group)) {
            throw new IllegalStateException("OnsConsumerEndpointRegistrar requires consumerId or @MQEventListener(group=...)");
        }
        String topic = java.util.Objects.isNull(definition.getTopic()) ? properties.getTopic() : definition.getTopic();
        if (java.util.Objects.isNull(topic)) {
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
            if (java.util.Objects.nonNull(message.getTag())) {
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
