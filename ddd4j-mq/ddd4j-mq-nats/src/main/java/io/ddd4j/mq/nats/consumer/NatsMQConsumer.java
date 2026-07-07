package io.ddd4j.mq.nats.consumer;

import io.ddd4j.mq.consume.MQEventConsumer;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.Message;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * NATS JetStream 消费者实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQEventConsumer}，在 {@link #subscribe(MQListener, MQEventCallback)} 中建立 JetStream Push Consumer，
 * 收到消息后做 tag 过滤、提取 payload 字符串、构建 {@link io.ddd4j.mq.nats.ack.NatsAcknowledgment}，
 * 通过 {@link MQEventCallback} 交给 core 统一处理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class NatsMQConsumer implements MQEventConsumer {

    private final Connection connection;
    private final MQProperties properties;

    /**
     * 构造 NATS 消费者。
     *
     * @param connection NATS 连接
     * @param properties MQ 全局配置
     */
    public NatsMQConsumer(Connection connection, MQProperties properties) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void subscribe(MQListener listener, MQEventCallback onEvent) {
        String subject = resolveSubject(listener);
        try {
            JetStream jetStream = connection.jetStream();
            Dispatcher dispatcher = connection.createDispatcher(msg -> {
            });
            PushSubscribeOptions options = PushSubscribeOptions.builder()
                    .durable(listener.getGroup())
                    .build();
            jetStream.subscribe(subject, dispatcher, msg -> onMessage(msg, listener, onEvent), false, options);
            log.info("Registered NATS JetStream listener: subject={}, durable={}, ackMode={}",
                    subject, listener.getGroup(), properties.getConsumer().getAckMode());
        } catch (Exception ex) {
            log.warn("JetStream subscribe failed for subject={}, falling back to core NATS: {}",
                    subject, ex.getMessage());
            Dispatcher dispatcher = connection.createDispatcher(msg -> onMessage(msg, listener, onEvent));
            dispatcher.subscribe(subject);
            log.info("Registered NATS core listener: subject={}", subject);
        }
    }

    private void onMessage(Message natsMessage, MQListener listener, MQEventCallback onEvent) {
        try {
            String subject = natsMessage.getSubject();
            String tag = extractTag(subject);
            if (!TagMatcher.match(tag, listener.getTags())) {
                return;
            }
            String payload = new String(natsMessage.getData(), StandardCharsets.UTF_8);
            String messageId = natsMessage.getSID();
            Acknowledgment ack = io.ddd4j.mq.nats.ack.NatsAcknowledgmentFactory.fromNatsMessage(natsMessage)
                    .map(a -> (Acknowledgment) a)
                    .orElse(null);
            onEvent.onEvent(payload, messageId, null, tag, ack);
            if (!properties.getConsumer().isManualAck() && Objects.nonNull(ack) && !ack.isAcknowledged()
                    && Objects.nonNull(natsMessage.metaData())) {
                ack.ack();
            }
        } catch (Throwable ex) {
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

    /**
     * 解析监听器定义对应的 NATS subject：{@code [namespace].topic[.tag]}。
     */
    private static String resolveSubject(MQListener listener) {
        String ns = Objects.isNull(listener.getNamespace()) ? "" : listener.getNamespace();
        String topic = Objects.isNull(listener.getTopic()) || listener.getTopic().isEmpty()
                ? "ddd4j.default.topic" : listener.getTopic();
        String tag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        String base = ns.isEmpty() ? topic : ns + "." + topic;
        return Objects.isNull(tag) ? base : base + "." + tag;
    }

    /**
     * 从 subject 末段提取 tag。
     */
    private static String extractTag(String subject) {
        if (Objects.isNull(subject)) {
            return null;
        }
        int lastDot = subject.lastIndexOf('.');
        return lastDot >= 0 ? subject.substring(lastDot + 1) : subject;
    }
}
