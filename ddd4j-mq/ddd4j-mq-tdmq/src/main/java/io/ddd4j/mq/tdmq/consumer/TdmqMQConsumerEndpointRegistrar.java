package io.ddd4j.mq.tdmq.consumer;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import io.ddd4j.mq.tdmq.ack.TdmqMessageAcknowledgment;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import io.ddd4j.mq.tdmq.client.TdmqSubscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@code @MQEventListener} 注册为 TDMQ 消费端点（占位客户端使用进程内总线）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class TdmqMQConsumerEndpointRegistrar implements AutoCloseable {

    private final TdmqClient tdmqClient;
    private final Ddd4jMQProperties properties;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<TdmqSubscription> subscriptions = new CopyOnWriteArrayList<>();

    /**
     * 注册单个监听器定义。
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        String topic = MQListenerEndpointNaming.physicalTopic(properties, definition);
        String tag = MQListenerEndpointNaming.resolveTag(definition.getTags());
        String group = definition.getGroup();

        TdmqSubscription subscription = tdmqClient.subscribe(topic, tag, group,
                (messageId, correlationId, payload, ackCallback) ->
                        onMessage(messageId, correlationId, payload, ackCallback, definition, handler));
        subscriptions.add(subscription);
        registeredDefinitions.add(definition);

        log.info("Registered TDMQ listener: topic={}, tag={}, group={}, clientReady={}",
                topic, tag, group, tdmqClient.isReady());
    }

    /**
     * 批量注册监听器。
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions found for TDMQ");
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("TDMQ consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    @Override
    public void close() {
        for (TdmqSubscription subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Exception ex) {
                log.warn("Failed to close TDMQ subscription", ex);
            }
        }
        subscriptions.clear();
    }

    /**
     * 返回已登记的监听器定义。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 处理 TDMQ 消息并委托 {@link MQConsumerHandler}。
     */
    private void onMessage(
            String messageId,
            String correlationId,
            byte[] payload,
            java.util.function.Consumer<Boolean> ackCallback,
            MQListenerDefinition definition,
            MQConsumerHandler handler) {

        try {
            String payloadText = payload == null ? "" : new String(payload, StandardCharsets.UTF_8);
            Map<String, Object> headers = new HashMap<>();
            headers.put("tdmq.topic", definition.getTopic());
            headers.put("tdmq.tag", definition.getTags());

            MessageAcknowledgment ack = new TdmqMessageAcknowledgment(
                    messageId, correlationId, correlationId == null ? 0L : correlationId.hashCode(), ackCallback);

            MQMessage<String> mqMessage = MQMessage.of(
                    payloadText, headers, messageId, correlationId, payloadText);
            handler.handle(mqMessage, ack);
            if (!properties.getConsumer().isManualAck() && !ack.isAcknowledged()) {
                ack.ack();
            }
        } catch (Exception ex) {
            log.error("TDMQ consumer failed: bean={}, method={}",
                    beanLabel(definition), definition.getMethod().getName(), ex);
            ackCallback.accept(true);
        }
    }

    private String beanLabel(MQListenerDefinition definition) {
        if (definition.getBean() != null) {
            return definition.getBean().getClass().getSimpleName();
        }
        if (definition.getBeanName() != null) {
            return definition.getBeanName();
        }
        return definition.getMethod().getDeclaringClass().getSimpleName();
    }
}
