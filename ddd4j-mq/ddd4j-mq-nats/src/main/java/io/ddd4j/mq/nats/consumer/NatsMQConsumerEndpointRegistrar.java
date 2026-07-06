package io.ddd4j.mq.nats.consumer;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.consume.NoOpAcknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.MessageConverter;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.nats.ack.NatsAcknowledgmentFactory;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.listener.EndpointNaming;
import io.nats.client.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@code @EventListener} 动态注册为 NATS JetStream Push Consumer。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class NatsMQConsumerEndpointRegistrar implements AutoCloseable {

    private final Connection connection;
    private final MQProperties properties;
    private final List<ListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<JetStreamSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final List<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();

    /**
     * NATS 原生消息 → Message 转换器。
     */
    private static final MessageConverter<io.nats.client.Message> CONVERTER = natsMessage -> {
        String payloadText = new String(natsMessage.getData(), StandardCharsets.UTF_8);
        Map<String, Object> headers = new HashMap<>();
        headers.put("nats.subject", natsMessage.getSubject());
        return Message.of(
                payloadText,
                headers,
                natsMessage.getSID(),
                natsMessage.getReplyTo(),
                natsMessage);
    };

    /**
     * 注册单个监听器定义。
     */
    public void register(ListenerDefinition definition, ConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        String subject = EndpointNaming.physicalTopic(properties, definition);
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
                    msg -> onMessage(msg, handler),
                    false,
                    options);
            subscriptions.add(subscription);
            registeredDefinitions.add(definition);
            log.info("Registered NATS JetStream listener: subject={}, durable={}, ackMode={}",
                    subject, definition.getGroup(), properties.getConsumer().getAckMode());
        } catch (Exception ex) {
            log.warn("JetStream subscribe failed for subject={}, falling back to core NATS: {}",
                    subject, ex.getMessage());
            registerCoreNats(subject, handler);
        }
    }

    /**
     * 核心 NATS 订阅（无 JetStream ack，使用 NoOp 确认）。
     */
    private void registerCoreNats(String subject, ConsumerHandler handler) {
        Dispatcher dispatcher = connection.createDispatcher(msg -> onMessage(msg, handler));
        dispatcher.subscribe(subject);
        dispatchers.add(dispatcher);
        log.info("Registered NATS core listener: subject={}", subject);
    }

    /**
     * 批量注册监听器。
     */
    public void registerAll(List<ListenerDefinition> definitions, ConsumerHandler handler) {
        if (Objects.isNull(definitions) || definitions.isEmpty()) {
            return;
        }
        for (ListenerDefinition definition : definitions) {
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
    public List<ListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 处理 NATS 消息：MessageConverter 转换 → ACK 构造 → handler.handle → autoAck 兜底。
     */
    private void onMessage(io.nats.client.Message natsMessage, ConsumerHandler handler) {
        try {
            Message<?> message = CONVERTER.convert(natsMessage);
            Acknowledgment ack = NatsAcknowledgmentFactory.fromNatsMessage(natsMessage)
                    .map(a -> (Acknowledgment) a)
                    .orElseGet(NoOpAcknowledgment::new);
            handler.handle(message, ack);
            if (!properties.getConsumer().isManualAck() && !ack.isAcknowledged()
                    && Objects.nonNull(natsMessage.metaData())) {
                ack.ack();
            }
        } catch (Exception ex) {
            log.error("NATS consumer failed: subject={}", natsMessage.getSubject(), ex);
            if (Objects.nonNull(natsMessage.metaData())) {
                try {
                    natsMessage.nak();
                } catch (Exception nakEx) {
                    log.warn("Failed to nak NATS message after error", nakEx);
                }
            }
        }
    }
}
