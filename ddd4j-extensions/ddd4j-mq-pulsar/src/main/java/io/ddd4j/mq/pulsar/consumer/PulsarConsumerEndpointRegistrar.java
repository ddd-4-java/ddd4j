package io.ddd4j.mq.pulsar.consumer;

import io.ddd4j.mq.pulsar.ack.PulsarMessageAcknowledgment;
import io.ddd4j.mq.pulsar.ack.PulsarMessageAcknowledgmentFactory;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.NoOpMessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@code @MQEventListener} 动态注册为 Pulsar 消费端点。
 */
@Slf4j
@RequiredArgsConstructor
public class PulsarConsumerEndpointRegistrar implements AutoCloseable {

    private final ApplicationContext applicationContext;
    private final Ddd4jMQProperties properties;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> consumers = new CopyOnWriteArrayList<>();

    /**
     * 注册单个监听器定义。
     *
     * @param definition 监听器定义
     * @param handler    消费处理函数
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        PulsarClient pulsarClient = applicationContext.getBean(PulsarClient.class);
        String topic = MQListenerEndpointNaming.physicalTopic(properties, definition);
        String subscriptionName = definition.getGroup();
        String endpointId = MQListenerEndpointNaming.endpointId("pulsar", definition);
        String queueName = MQListenerEndpointNaming.queueName(definition);

        try {
            Consumer<String> consumer = pulsarClient.newConsumer(Schema.STRING)
                    .topic(topic)
                    .subscriptionName(subscriptionName)
                    .subscriptionType(SubscriptionType.Shared)
                    .consumerName(endpointId)
                    .messageListener((c, msg) -> onMessage(c, msg, definition, handler))
                    .subscribe();
            consumers.add(consumer);
            registeredDefinitions.add(definition);

            log.info("Registered Pulsar listener: id={}, topic={}, subscription={}, queueName={}, ackMode={}",
                    endpointId, topic, subscriptionName, queueName, properties.getConsumer().getAckMode());
        } catch (PulsarClientException ex) {
            throw new IllegalStateException(
                    "Failed to start Pulsar consumer for topic=" + topic + ", subscription=" + subscriptionName, ex);
        }
    }

    /**
     * 批量注册监听器（启动阶段调用）。
     *
     * @param definitions 监听器定义列表
     * @param handler     统一消费处理函数
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions found for Pulsar");
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("Pulsar consumer registrar initialized with {} listener(s), ackMode={}",
                registeredDefinitions.size(), properties.getConsumer().getAckMode());
    }

    @Override
    public void close() {
        for (Consumer<String> consumer : consumers) {
            try {
                consumer.close();
            } catch (Exception ex) {
                log.warn("Failed to close Pulsar consumer", ex);
            }
        }
        consumers.clear();
    }

    /**
     * 返回已登记的监听器定义（只读视图）。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 处理 Pulsar 消息并委托 {@link MQConsumerHandler}。
     */
    private void onMessage(
            Consumer<String> consumer,
            Message<String> pulsarMessage,
            MQListenerDefinition definition,
            MQConsumerHandler handler) {

        try {
            String payloadText = pulsarMessage.getValue();
            org.springframework.messaging.Message<String> springMessage = MessageBuilder
                    .withPayload(payloadText)
                    .setHeader(PulsarMessageAcknowledgment.HEADER_PULSAR_CONSUMER, consumer)
                    .setHeader(PulsarMessageAcknowledgment.HEADER_PULSAR_MESSAGE, pulsarMessage)
                    .build();

            Map<String, Object> headers = new HashMap<>(springMessage.getHeaders());
            MQMessage<String> mqMessage = MQMessage.of(
                    payloadText,
                    headers,
                    pulsarMessage.getMessageId().toString(),
                    pulsarMessage.getProperty("correlationId"),
                    springMessage);

            MessageAcknowledgment ack = PulsarMessageAcknowledgmentFactory.fromSpringMessage(springMessage)
                    .map(a -> (MessageAcknowledgment) a)
                    .orElseGet(NoOpMessageAcknowledgment::new);

            handler.handle(mqMessage, ack);
        } catch (Exception ex) {
            log.error("Pulsar consumer failed: bean={}, method={}",
                    definition.getBean().getClass().getSimpleName(),
                    definition.getMethod().getName(),
                    ex);
            if (properties.getConsumer().isManualAck()) {
                consumer.negativeAcknowledge(pulsarMessage);
            }
            throw new RuntimeException(ex);
        }
    }
}
