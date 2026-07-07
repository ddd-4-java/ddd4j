package io.ddd4j.mq.nats;

import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.util.TagMatcher;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * NATS JetStream 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQClient}：
 * <ul>
 *   <li>{@link #initProducer} —— 建 NATS {@link Connection}，
 *       返回 {@link Consumer<MQEvent>}。优先 JetStream.publish，失败回退 core NATS。
 *       subject 使用 {@code [namespace.]topic[.tag]}</li>
 *   <li>{@link #initConsumer} —— 建 JetStream Push Consumer（失败回退 core NATS Dispatcher），
 *       收到消息后做 tag 过滤、反序列化 → 构建 {@link NatsAcknowledgment} →
 *       调 {@link #consume} 统一消费。autoAck=true 时由框架自动 ack</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class NatsClient implements MQClient {

    private final NatsProperties properties;
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();

    public NatsClient(NatsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "nats";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        Connection conn = connection();
        return event -> publish(conn, event);
    }

    private void publish(Connection conn, MQEvent event) {
        try {
            String subject = resolveSubject(event.getNamespace(), event.getTopic(), event.getTag());
            String payload = JsonKit.toJson(event);
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            try {
                JetStream jetStream = conn.jetStream();
                jetStream.publish(subject, body);
                logger().debug("Published NATS JetStream event, subject={}, msgId={}", subject, event.getMsgId());
            } catch (IOException | JetStreamApiException ex) {
                conn.publish(subject, body);
                logger().debug("Published NATS core event, subject={}, msgId={}", subject, event.getMsgId());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Publish NATS event failed", ex);
        }
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        Connection conn = connection();
        boolean autoAck = mqProperties.isAutoAck();
        String subject = resolveSubject(listener.getNamespace(), listener.getTopic(),
                TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null));
        try {
            JetStream jetStream = conn.jetStream();
            Dispatcher dispatcher = conn.createDispatcher(msg -> {
            });
            PushSubscribeOptions options = PushSubscribeOptions.builder()
                    .durable(listener.getGroup())
                    .build();
            jetStream.subscribe(subject, dispatcher, msg -> onMessage(msg, listener, autoAck), false, options);
            log.info("Registered NATS JetStream listener: subject={}, durable={}", subject, listener.getGroup());
        } catch (Exception ex) {
            log.warn("JetStream subscribe failed for subject={}, falling back to core NATS: {}",
                    subject, ex.getMessage());
            Dispatcher dispatcher = conn.createDispatcher(msg -> onMessage(msg, listener, autoAck));
            dispatcher.subscribe(subject);
            log.info("Registered NATS core listener: subject={}", subject);
        }
        return true;
    }

    private void onMessage(Message natsMessage, MQListener listener, boolean autoAck) {
        try {
            String subject = natsMessage.getSubject();
            String tag = extractTag(subject);
            if (!TagMatcher.match(tag, listener.getTags())) {
                return;
            }
            String payload = new String(natsMessage.getData(), StandardCharsets.UTF_8);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            Acknowledgment ack = new NatsAcknowledgment(natsMessage);
            consume(listener, event, ack);
            if (!autoAck && Objects.nonNull(ack) && !ack.isAcknowledged()
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

    // ========================= 工具 =========================

    /**
     * 解析发布 / 订阅 subject：{@code [namespace.]topic[.tag]}。
     */
    private static String resolveSubject(String namespace, String topic, String tag) {
        String base = io.ddd4j.kit.lang.StrKit.hasText(topic) ? topic : "ddd4j.default.topic";
        if (io.ddd4j.kit.lang.StrKit.hasText(namespace)) {
            base = namespace + "." + base;
        }
        return io.ddd4j.kit.lang.StrKit.hasText(tag) ? base + "." + tag : base;
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

    private synchronized Connection connection() {
        Connection c = connectionRef.get();
        if (Objects.isNull(c)) {
            c = properties.connect();
            connectionRef.set(c);
        }
        return c;
    }
}
