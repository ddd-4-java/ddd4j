package io.ddd4j.mq.nats.consumer;

import io.ddd4j.mq.nats.acknowledgment.NatsMessageAcknowledgmentFactory;
import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.acknowledgment.NoOpMessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@code @MQEventListener} 动态注册为 NATS JetStream Push Consumer。
 */
@Slf4j
@RequiredArgsConstructor
public class NatsMQConsumerEndpointRegistrar implements AutoCloseable {

    private final Connection connection;
    private final Ddd4jMQProperties properties;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<JetStreamSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final List<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();

    /**
     * 注册单个监听器定义。
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        String subject = MQListenerEndpointNaming.physicalTopic(properties, definition);
        try {
            JetStream jetStream = connection.jetStream();
            Dispatcher dispatcher = connection.createDispatcher(msg -> {
            });
            dispatchers.add(dispatcher);

            PushSubscribeOptions options = PushSubscribeOptions.builder()
                    .durable(definition.getGroup())
                    .build();
            JetStreamSubscription subscription = jetStream.subscribe(
                    subject,
                    dispatcher,
                    msg -> onMessage(msg, definition, handler),
                    false,
                    options);
            subscriptions.add(subscription);
            registeredDefinitions.add(definition);
            log.info("Registered NATS JetStream listener: subject={}, durable={}, ackMode={}",
                    subject, definition.getGroup(), properties.getConsumer().getAckMode());
        } catch (Exception ex) {
            log.warn("JetStream subscribe failed for subject={}, falling back to core NATS: {}",
                    subject, ex.getMessage());
            registerCoreNats(subject, definition, handler);
        }
    }

    /**
     * 核心 NATS 订阅（无 JetStream ack，使用 NoOp 确认）。
     */
    private void registerCoreNats(String subject, MQListenerDefinition definition, MQConsumerHandler handler) {
        Dispatcher dispatcher = connection.createDispatcher(msg -> onMessage(msg, definition, handler));
        dispatcher.subscribe(subject);
        dispatchers.add(dispatcher);
        registeredDefinitions.add(definition);
        log.info("Registered NATS core listener: subject={}", subject);
    }

    /**
     * 批量注册监听器。
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions found for NATS");
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("NATS consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    @Override
    public void close() {
        for (JetStreamSubscription subscription : subscriptions) {
            try {
                subscription.unsubscribe();
            } catch (Exception ex) {
                log.warn("Failed to unsubscribe NATS JetStream subscription", ex);
            }
        }
        subscriptions.clear();
        dispatchers.clear();
    }

    /**
     * 返回已登记的监听器定义。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 处理 NATS 消息并委托 {@link MQConsumerHandler}。
     */
    private void onMessage(Message natsMessage, MQListenerDefinition definition, MQConsumerHandler handler) {
        try {
            String payloadText = new String(natsMessage.getData(), StandardCharsets.UTF_8);
            Map<String, Object> headers = new HashMap<>();
            headers.put("nats.subject", natsMessage.getSubject());
            MQMessage<String> mqMessage = MQMessage.of(
                    payloadText,
                    headers,
                    natsMessage.getSID(),
                    natsMessage.getReplyTo(),
                    natsMessage);

            MessageAcknowledgment ack = NatsMessageAcknowledgmentFactory.fromNatsMessage(natsMessage)
                    .map(a -> (MessageAcknowledgment) a)
                    .orElseGet(NoOpMessageAcknowledgment::new);
            handler.handle(mqMessage, ack);
            if (!properties.getConsumer().isManualAck() && !ack.isAcknowledged()
                    && natsMessage.metaData() != null) {
                ack.ack();
            }
        } catch (Exception ex) {
            log.error("NATS consumer failed: bean={}, method={}",
                    beanLabel(definition), definition.getMethod().getName(), ex);
            if (natsMessage.metaData() != null) {
                try {
                    natsMessage.nak();
                } catch (Exception nakEx) {
                    log.warn("Failed to nak NATS message after error", nakEx);
                }
            }
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
