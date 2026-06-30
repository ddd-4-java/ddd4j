package io.ddd4j.mq.rocketmq;

import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import io.ddd4j.mq.registry.MQTagMatcher;
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

    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
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
            MQListenerDefinition definition,
            MQConsumerHandler handler) {
        for (MessageExt message : messages) {
            if (!MQTagMatcher.match(message.getTags(), definition.getTags())) {
                continue;
            }
            RocketMessageAcknowledgment ack = new RocketMessageAcknowledgment(message);
            try {
                handler.handle(toMessage(message), ack);
                if (ack.shouldReconsume()) {
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            } catch (Exception ex) {
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private static MQMessage<String> toMessage(MessageExt message) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(RocketMessageAcknowledgment.HEADER_ROCKET_MESSAGE, message);
        headers.put(MQMessages.HEADER_DESTINATION_TOPIC, message.getTopic());
        headers.put(MQMessages.HEADER_DESTINATION_TAG, message.getTags());
        headers.put(MQMessages.HEADER_TENANT_ID, message.getUserProperty(MQMessages.HEADER_TENANT_ID));
        return MQMessage.of(
                new String(message.getBody(), StandardCharsets.UTF_8),
                headers,
                message.getMsgId(),
                message.getKeys(),
                message);
    }

    private String resolveGroup(MQListenerDefinition definition) {
        if (hasText(definition.getGroup())) {
            return definition.getGroup();
        }
        return rocketProperties.getConsumerGroupPrefix() + "-" + definition.bindingName();
    }

    private static String resolveTopic(MQListenerDefinition definition) {
        String concat = MQListenerEndpointNaming.resolveConcat(definition);
        return hasText(definition.getNamespace())
                ? definition.getNamespace() + concat + definition.getTopic()
                : definition.getTopic();
    }

    private static String subscriptionExpression(String tags) {
        if (tags == null || tags.isBlank() || tags.contains("-")) {
            return "*";
        }
        return tags;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
