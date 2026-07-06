package io.ddd4j.mq.ons.consumer;

import com.aliyun.openservices.ons.api.*;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.MessageConverter;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.ons.ack.OnsAcknowledgment;
import io.ddd4j.mq.ons.spi.OnsMQProperties;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.listener.TagMatcher;

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

    public void register(ListenerDefinition definition, ConsumerHandler handler) {
        String group = Objects.isNull(definition.getGroup()) || io.ddd4j.kit.lang.StrKit.isBlank(definition.getGroup())
                ? properties.getConsumerId() : definition.getGroup();
        if (Objects.isNull(group)) {
            throw new IllegalStateException("OnsConsumerEndpointRegistrar requires consumerId or @EventListener(group=...)");
        }
        String topic = Objects.isNull(definition.getTopic()) ? properties.getTopic() : definition.getTopic();
        if (Objects.isNull(topic)) {
            throw new IllegalStateException("OnsConsumerEndpointRegistrar requires topic");
        }
        String tag = TagMatcher.findIncludes(definition.getTags()).stream().findFirst().orElse(null);
        Consumer consumer = ONSFactory.createConsumer(properties.sessionProperties(group));
        consumer.subscribe(topic, properties.subscriptionExpression(tag),
                (msg, ctx) -> handleMessage(msg, ctx, definition, handler));
        consumer.start();
    }

    private Action handleMessage(Message message, ConsumeContext context, ListenerDefinition def,
                                 ConsumerHandler handler) {
        try {
            if (!TagMatcher.match(message.getTag(), def.getTags())) {
                return Action.CommitMessage;
            }
            MessageConverter<Message> converter = nativeMsg -> {
                Map<String, Object> headers = new HashMap<>();
                headers.put(MessageHeaders.HEADER_DESTINATION_TOPIC, nativeMsg.getTopic());
                if (Objects.nonNull(nativeMsg.getTag())) {
                    headers.put(MessageHeaders.HEADER_DESTINATION_TAG, nativeMsg.getTag());
                }
                headers.put(OnsAcknowledgment.HEADER_ONS_MESSAGE, nativeMsg);
                headers.put(OnsAcknowledgment.HEADER_ONS_CONTEXT, context);
                return Message.of(
                        new String(nativeMsg.getBody(), StandardCharsets.UTF_8),
                        headers,
                        nativeMsg.getMsgID(),
                        nativeMsg.getKey(),
                        nativeMsg);
            };
            Message<?> mq = converter.convert(message);
            OnsAcknowledgment ack = new OnsAcknowledgment(context, message);
            handler.handle(mq, ack);
            return ack.action();
        } catch (Exception ex) {
            return Action.ReconsumeLater;
        }
    }
}
