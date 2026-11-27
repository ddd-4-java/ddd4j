package io.ddd4j.mq.nats;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import io.nats.client.*;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * NATS JetStream 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>NATS 无原生 broker-side tag filter（仅 subject 通配），应用层 {@link TagMatcher} tag 过滤保留。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : NatsMQClient ###")
public class NatsMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final NatsProperties properties;
    /** \u7528\u4e8e\u5ef6\u8fdf\u521b\u5efa\u5e76\u590d\u7528\u4ee3\u7406\u8fde\u63a5\u7684\u5f15\u7528\u3002 */
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();

    /**
     * 双构造 1：注入已初始化的原生 NATS {@link Connection}（用于 runtime 集成自动注入）。
     *
     * @param connection 原生 NATS 连接
     */
    public NatsMQClient(Connection connection) {
        this.properties = new NatsProperties();
        if (Objects.nonNull(connection)) {
            this.connectionRef.set(connection);
        }
    }

    /**
     * 双构造 2：传入配置，{@link #connection()} 时 lazy 构造原生 NATS 连接。
     *
     * @param properties NATS 配置
     */
    public NatsMQClient(NatsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"nats"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "nats";
    }

    /**
     * NATS \u65e0\u539f\u751f broker-side tag selector\uff0c\u4ec5 subject \u901a\u914d \u2192 \u5f3a\u5236\u5e94\u7528\u5c42 {@link TagMatcher} \u8fc7\u6ee4\u3002
     *
     * @return \u5f53\u524d\u4ee3\u7406\u662f\u5426\u652f\u6301\u6309\u6807\u7b7e\u8fc7\u6ee4
     */
    @Override
    public boolean supportsBrokerTagFilter() {
        return false;
    }

    // ========================= 生产者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>此实现调用 <code>connection()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        Connection conn = connection();
        return event -> {
            String payload = serialization().serialize(event);
            String subject = resolveTopic(event, mqProperties);
            try {
                byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                Headers headers = messageHeaders(event);
                // JetStream 优先，失败回落 core NATS（如未启用 JetStream）
                try {
                    JetStream jetStream = conn.jetStream();
                    jetStream.publish(subject, headers, body);
                } catch (IOException | JetStreamApiException ex) {
                    conn.publish(subject, headers, body);
                }
            } catch (Exception ex) {
                log.error("Publish NATS [{}]: {} failed!", subject, payload, ex);
                throw new IllegalStateException("Publish NATS event failed", ex);
            }
            log.info("Publish MQ [{}]: {}", subject, payload);
        };
    }

    // ========================= 消费者 =========================

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>connection()</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        Connection conn = connection();
        String subject = resolveTopic(listener, mqProperties);
        try {
            JetStream jetStream = conn.jetStream();
            Dispatcher dispatcher = conn.createDispatcher(msg -> {
            });
            PushSubscribeOptions options = PushSubscribeOptions.builder()
                    .durable(listener.getGroup())
                    .build();
            jetStream.subscribe(subject, dispatcher, msg -> onMessage(msg, listener), false, options);
            log.info("Registered NATS JetStream listener: subject={}, durable={}", subject, listener.getGroup());
        } catch (Exception ex) {
            log.warn("JetStream subscribe failed for subject={}, falling back to core NATS: {}",
                    subject, ex.getMessage());
            Dispatcher dispatcher = conn.createDispatcher(msg -> onMessage(msg, listener));
            dispatcher.subscribe(subject);
            log.info("Registered NATS core listener: subject={}", subject);
        }
        return true;
    }

    /**
     * \u89e3\u7801 NATS \u6d88\u606f\u3001\u6062\u590d\u6d88\u606f\u5143\u6570\u636e\u5e76\u8f6c\u4ea4\u5f53\u524d\u76d1\u542c\u5668\u3002
     * @param natsMessage \u7531 NATS \u539f\u751f\u8ba2\u9605\u56de\u8c03\u4ea4\u4ed8\u7684\u6d88\u606f
     * @param listener \u5f53\u524d\u4ee3\u7406\u8ba2\u9605\u5bf9\u5e94\u7684 DDD4J \u6d88\u606f\u76d1\u542c\u5668
     */
    private void onMessage(Message natsMessage, MQListener listener) {
        try {
            String payload = new String(natsMessage.getData(), StandardCharsets.UTF_8);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                log.warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(defaultConcat()));
                return;
            }
            restoreMessageId(event, messageId(natsMessage.getHeaders()));
            // 应用层 tag 过滤（tag 取 subject 末段）
            String tag = null;
            String subject = natsMessage.getSubject();
            int lastDot = subject.lastIndexOf('.');
            if (lastDot >= 0) {
                tag = subject.substring(lastDot + 1);
            }
            if (!TagMatcher.match(tag, listener.getTags())) {
                return;
            }
            NatsAcknowledgment ack = new NatsAcknowledgment(natsMessage);
            consume(listener, event, ack);
            if (!ack.isAcknowledged()) {
                ack.ackSingle();
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

    // ========================= 连接管理 =========================

    /**
     * \u53d6\u5f97\u5f53\u524d\u9002\u914d\u5668\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef\uff0c\u7f3a\u5c11\u5b9e\u4f8b\u65f6\u5efa\u7acb\u8fde\u63a5\u3002
     * @return \u4f9b\u53d1\u5e03\u6216\u6d88\u8d39\u64cd\u4f5c\u4f7f\u7528\u7684\u539f\u751f\u8fde\u63a5\u6216\u5ba2\u6237\u7aef
     */
    private synchronized Connection connection() {
        Connection c = connectionRef.get();
        if (Objects.isNull(c)) {
            c = properties.connect();
            connectionRef.set(c);
        }
        return c;
    }

    /**
     * \u4ece DDD4J \u4e8b\u4ef6\u7ec4\u88c5 NATS \u4f20\u8f93\u4fa7\u6d88\u606f\u5934\u3002
     * @param event \u5f53\u524d\u53d1\u5e03\u6216\u5f85\u8865\u9f50\u5143\u6570\u636e\u7684 DDD4J \u6d88\u606f\u4e8b\u4ef6
     * @return \u53d1\u9001\u7ed9 NATS \u7684\u5934\u5b57\u6bb5\u96c6\u5408
     */
    static Headers messageHeaders(MQEvent event) {
        Headers headers = new Headers();
        if (StrKit.isNotEmpty(event.getMsgId())) {
            headers.put(MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
        }
        return headers;
    }

    /**
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param headers \u6d88\u606f\u968f\u534f\u8bae\u9644\u5e26\u7684\u5934\u5b57\u6bb5
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static String messageId(Headers headers) {
        if (Objects.isNull(headers)) {
            return null;
        }
        String messageId = headers.getFirst(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : headers.getFirst(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }

    /**
     * \u5728\u6210\u529f\u53d6\u5230\u539f\u751f\u4e1a\u52a1\u6807\u8bc6\u65f6\u5c06\u5176\u6062\u590d\u5230\u4e8b\u4ef6\u3002
     * @param event \u5f53\u524d\u53d1\u5e03\u6216\u5f85\u8865\u9f50\u5143\u6570\u636e\u7684 DDD4J \u6d88\u606f\u4e8b\u4ef6
     * @param messageId \u4ece\u539f\u751f\u4ee3\u7406\u6d88\u606f\u89e3\u6790\u51fa\u7684\u6807\u8bc6
     */
    private static void restoreMessageId(MQEvent event, String messageId) {
        if (StrKit.isNotEmpty(messageId)) {
            event.setMsgId(messageId);
        }
    }
}
