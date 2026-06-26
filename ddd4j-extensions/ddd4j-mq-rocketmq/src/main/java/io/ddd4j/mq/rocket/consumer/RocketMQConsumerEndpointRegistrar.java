package io.ddd4j.mq.rocket.consumer;

import io.ddd4j.mq.rocket.ack.RocketMessageAcknowledgment;
import io.ddd4j.mq.rocket.ack.RocketMessageAcknowledgmentFactory;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.NoOpMessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 将 {@code @MQEventListener} 动态注册为 RocketMQ {@link DefaultMQPushConsumer}。
 */
@Slf4j
@RequiredArgsConstructor
public class RocketMQConsumerEndpointRegistrar implements AutoCloseable {

    private final ApplicationContext applicationContext;
    private final Ddd4jMQProperties properties;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<DefaultMQPushConsumer> consumers = new CopyOnWriteArrayList<>();

    /**
     * 注册单个监听器定义。
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        String topic = buildTopic(definition);
        String consumerGroup = definition.getGroup();
        String tag = resolveTag(definition.getTags());

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        applyNameServer(consumer);
        try {
            consumer.subscribe(topic, tag);
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                for (MessageExt messageExt : messages) {
                    onMessage(messageExt, definition, handler);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
            consumers.add(consumer);
            registeredDefinitions.add(definition);
            log.info("Registered RocketMQ listener: topic={}, group={}, tag={}", topic, consumerGroup, tag);
        } catch (MQClientException ex) {
            throw new IllegalStateException("Failed to start RocketMQ consumer for topic=" + topic, ex);
        }
    }

    /**
     * 批量注册监听器。
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("RocketMQ consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    @Override
    public void close() {
        for (DefaultMQPushConsumer consumer : consumers) {
            consumer.shutdown();
        }
        consumers.clear();
    }

    /**
     * 返回已登记的监听器定义。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 处理 RocketMQ 消息并委托 {@link MQConsumerHandler}。
     */
    private void onMessage(MessageExt messageExt, MQListenerDefinition definition, MQConsumerHandler handler) {
        try {
            String payloadText = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            Consumer<Boolean> ackCallback = success -> {
            };
            org.springframework.messaging.Message<String> springMessage = MessageBuilder
                    .withPayload(payloadText)
                    .setHeader(RocketMessageAcknowledgment.HEADER_ROCKET_MESSAGE, messageExt)
                    .setHeader(RocketMessageAcknowledgment.HEADER_ROCKET_ACK_CALLBACK, ackCallback)
                    .build();

            Map<String, Object> headers = new HashMap<>(springMessage.getHeaders());
            MQMessage<String> mqMessage = MQMessage.of(
                    payloadText,
                    headers,
                    messageExt.getMsgId(),
                    messageExt.getKeys(),
                    springMessage);

            MessageAcknowledgment ack = RocketMessageAcknowledgmentFactory.fromSpringMessage(springMessage)
                    .map(a -> (MessageAcknowledgment) a)
                    .orElseGet(NoOpMessageAcknowledgment::new);
            handler.handle(mqMessage, ack);
        } catch (Exception ex) {
            log.error("RocketMQ consumer failed: bean={}, method={}",
                    definition.getBean().getClass().getSimpleName(),
                    definition.getMethod().getName(),
                    ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * 从环境注入 NameServer 地址。
     */
    private void applyNameServer(DefaultMQPushConsumer consumer) {
        RocketMQProperties rocketMQProperties = applicationContext.getBean(RocketMQProperties.class);
        if (rocketMQProperties != null && StringUtils.hasText(rocketMQProperties.getNameServer())) {
            consumer.setNamesrvAddr(rocketMQProperties.getNameServer());
        }
    }

    private String buildTopic(MQListenerDefinition definition) {
        String concat = StringUtils.hasText(definition.getConcat()) ? definition.getConcat() : ".";
        String namespace = StringUtils.hasText(definition.getNamespace())
                ? definition.getNamespace()
                : properties.getNamespace();
        return namespace + concat + definition.getTopic();
    }

    private String resolveTag(String tags) {
        if (!StringUtils.hasText(tags)) {
            return "*";
        }
        String trimmed = tags.trim();
        if (trimmed.contains("||")) {
            return trimmed.substring(0, trimmed.indexOf("||")).trim();
        }
        return trimmed;
    }
}
