package io.ddd4j.mq.tdmq.consumer;

import io.ddd4j.mq.consume.ack.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.MessageConverter;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.tdmq.ack.TdmqAcknowledgmentFactory;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import io.ddd4j.mq.tdmq.client.TdmqSubscription;
import io.ddd4j.mq.tdmq.spi.TdmqMQProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 将 {@code @EventListener} 注册为 TDMQ 消费端点。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class TdmqMQConsumerEndpointRegistrar implements AutoCloseable {

    private final TdmqClient tdmqClient;
    private final MQProperties mqProperties;
    private final TdmqMQProperties tdmqProperties;
    private final List<ListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<TdmqSubscription> subscriptions = new CopyOnWriteArrayList<>();

    public TdmqMQConsumerEndpointRegistrar(TdmqClient tdmqClient,
                                           MQProperties mqProperties,
                                           TdmqMQProperties tdmqProperties) {
        this.tdmqClient = Objects.requireNonNull(tdmqClient, "tdmqClient");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.tdmqProperties = Objects.requireNonNull(tdmqProperties, "tdmqProperties");
    }

    public void register(ListenerDefinition definition, ConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");
        if (!tdmqProperties.isAutoStartConsumers()) {
            log.info("TDMQ listener registration skipped because autoStartConsumers=false");
            return;
        }

        String topic = resolveTopic(definition);
        String tagExpression = definition.getTags();
        String group = resolveGroup(definition);
        TdmqSubscription subscription = tdmqClient.subscribe(topic, tagExpression, group,
                (messageId, correlationId, payload, ackCallback) ->
                        onMessage(messageId, correlationId, payload, ackCallback, definition, handler));
        subscriptions.add(subscription);
        registeredDefinitions.add(definition);
        log.info("Registered TDMQ listener: topic={}, tags={}, group={}, clientReady={}",
                topic, tagExpression, group, tdmqClient.isReady());
    }

    public void registerAll(List<ListenerDefinition> definitions, ConsumerHandler handler) {
        if (Objects.isNull(definitions) || definitions.isEmpty()) {
            log.debug("No @EventListener definitions found for TDMQ");
            return;
        }
        for (ListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("TDMQ consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    public List<ListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
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

    private void onMessage(
            String messageId,
            String correlationId,
            byte[] payload,
            Consumer<Boolean> ackCallback,
            ListenerDefinition definition,
            ConsumerHandler handler) {

        try {
            MessageConverter<byte[]> converter =
                    nativePayload -> toMessage(messageId, correlationId, nativePayload, ackCallback, definition);
            Message<?> mqMessage = converter.convert(payload);
            Acknowledgment ack = TdmqAcknowledgmentFactory.from(mqMessage);
            handler.handle(mqMessage, ack);
            if (!mqProperties.getConsumer().isManualAck() && !ack.isAcknowledged()) {
                ack.ack();
            }
        } catch (Exception ex) {
            log.error("TDMQ consumer failed: bean={}, method={}",
                    beanLabel(definition), definition.getMethod().getName(), ex);
            ackCallback.accept(tdmqProperties.isRequeueOnError());
        }
    }

    private Message<String> toMessage(
            String messageId,
            String correlationId,
            byte[] payload,
            Consumer<Boolean> ackCallback,
            ListenerDefinition definition) {

        String payloadText = Objects.isNull(payload) ? "" : new String(payload, StandardCharsets.UTF_8);
        Map<String, Object> headers = new HashMap<>();
        headers.put("tdmq.topic", definition.getTopic());
        headers.put("tdmq.tags", definition.getTags());
        headers.put("tdmq.group", resolveGroup(definition));
        headers.put(TdmqAcknowledgmentFactory.HEADER_ACK_CALLBACK, ackCallback);
        headers.put(TdmqAcknowledgmentFactory.HEADER_DELIVERY_TAG,
                Objects.nonNull(correlationId) ? correlationId.hashCode() : 0L);
        return Message.of(payloadText, headers, messageId, correlationId, payloadText);
    }

    private String resolveGroup(ListenerDefinition definition) {
        if (Objects.nonNull(definition.getGroup()) && !io.ddd4j.kit.lang.StrKit.isBlank(definition.getGroup())) {
            return definition.getGroup();
        }
        return tdmqProperties.getDefaultGroup();
    }

    private String resolveTopic(ListenerDefinition definition) {
        String namespace = io.ddd4j.kit.lang.StrKit.isNotBlank(definition.getNamespace())
                ? definition.getNamespace()
                : mqProperties.getNamespace();
        if (io.ddd4j.kit.lang.StrKit.isBlank(namespace)) {
            return definition.getTopic();
        }
        return namespace + "." + definition.getTopic();
    }

    private String beanLabel(ListenerDefinition definition) {
        if (Objects.nonNull(definition.getBean())) {
            return definition.getBean().getClass().getSimpleName();
        }
        if (Objects.nonNull(definition.getBeanName())) {
            return definition.getBeanName();
        }
        return definition.getMethod().getDeclaringClass().getSimpleName();
    }
}
