package io.ddd4j.mq.rocketmq;

import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.MessageConverter;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.listener.EndpointNaming;
import io.ddd4j.mq.listener.TagMatcher;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Programmatic RocketMQ consumer registrar.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RocketMQConsumerEndpointRegistrar implements AutoCloseable {

    private final RocketMQProperties rocketProperties;
    private final RocketConsumerFactory consumerFactory;
    private final List<DefaultMQPushConsumer> consumers = new CopyOnWriteArrayList<>();

    public RocketMQConsumerEndpointRegistrar(RocketMQProperties rocketProperties) {
        this(rocketProperties, rocketProperties::newConsumer);
    }

    public RocketMQConsumerEndpointRegistrar(
            RocketMQProperties rocketProperties,
            RocketConsumerFactory consumerFactory) {
        this.rocketProperties = Objects.requireNonNull(rocketProperties, "rocketProperties");
        this.consumerFactory = Objects.requireNonNull(consumerFactory, "consumerFactory");
    }

    /**
     * RocketMQ 原生消息 {@link MessageExt} → {@link Message} 转换器。
     */
    private static final MessageConverter<MessageExt> CONVERTER = RocketMQConsumerEndpointRegistrar::toMessage;

    private static Message<String> toMessage(MessageExt message) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(RocketAcknowledgment.HEADER_ROCKET_MESSAGE, message);
        headers.put(MessageHeaders.HEADER_DESTINATION_TOPIC, message.getTopic());
        headers.put(MessageHeaders.HEADER_DESTINATION_TAG, message.getTags());
        headers.put(MessageHeaders.HEADER_TENANT_ID, message.getUserProperty(MessageHeaders.HEADER_TENANT_ID));
        return Message.of(
                new String(message.getBody(), StandardCharsets.UTF_8),
                headers,
                message.getMsgId(),
                message.getKeys(),
                message);
    }

    private static String resolveTopic(ListenerDefinition definition) {
        String concat = EndpointNaming.resolveSeparator(definition);
        return hasText(definition.getNamespace())
                ? definition.getNamespace() + concat + definition.getTopic()
                : definition.getTopic();
    }

    private static String subscriptionExpression(String tags) {
        if (Objects.isNull(tags) || io.ddd4j.kit.lang.StrKit.isBlank(tags) || tags.contains("-")) {
            return "*";
        }
        return tags;
    }

    private static boolean hasText(String s) {
        return Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }

    public void register(ListenerDefinition definition, ConsumerHandler handler) {
        try {
            DefaultMQPushConsumer consumer = consumerFactory.create(resolveGroup(definition));
            String topic = resolveTopic(definition);
            consumer.subscribe(topic, subscriptionExpression(definition.getTags()));
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) ->
                    consume(messages, definition, handler));
            if (rocketProperties.isAutoStartConsumers()) {
                consumer.start();
            }
            consumers.add(consumer);
        } catch (Exception ex) {
            throw new IllegalStateException("Register RocketMQ consumer failed", ex);
        }
    }

    @Override
    public void close() {
        consumers.forEach(DefaultMQPushConsumer::shutdown);
        consumers.clear();
    }

    private ConsumeConcurrentlyStatus consume(
            List<MessageExt> messages,
            ListenerDefinition definition,
            ConsumerHandler handler) {
        for (MessageExt message : messages) {
            if (!TagMatcher.match(message.getTags(), definition.getTags())) {
                continue;
            }
            RocketAcknowledgment ack = new RocketAcknowledgment(message);
            try {
                handler.handle(CONVERTER.convert(message), ack);
                if (ack.shouldReconsume()) {
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            } catch (Exception ex) {
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private String resolveGroup(ListenerDefinition definition) {
        if (hasText(definition.getGroup())) {
            return definition.getGroup();
        }
        return rocketProperties.getConsumerGroupPrefix() + "-" + definition.bindingName();
    }
}
