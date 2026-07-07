package io.ddd4j.mq.tdmq;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 腾讯云 TDMQ 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQClient}：
 * <ul>
 *   <li>{@link #initProducer} —— 返回 {@link Consumer<MQEvent>}，发消息时委托给底层 TDMQ 客户端
 *       （业务可在 {@link TdmqMQClient#setBrokerPublisher(BrokerPublisher)} 注入业务侧 SDK）</li>
 *   <li>{@link #initConsumer} —— 通过 {@link BrokerSubscriber} 建立订阅，tag 过滤后调
 *       {@link #consume} 统一消费，传入 {@link TdmqAcknowledgment} 实现不同级别 ack</li>
 * </ul>
 *
 * <p>由于 ddd4j-mq-tdmq 不直接依赖腾讯云官方 SDK（保持无依赖、零 Spring），
 * 实际的 publish/subscribe 由业务侧通过 {@link BrokerPublisher} / {@link BrokerSubscriber} 注入。
 * 当未注入时，{@link TdmqMQClient} 提供"内存总线"实现，仅供本地联调/测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class TdmqMQClient implements MQClient {

    private final TdmqProperties properties;
    private BrokerPublisher brokerPublisher;
    private BrokerSubscriber brokerSubscriber;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 双构造 1：仅 properties（业务可在 initProducer/initConsumer 之前注入 BrokerPublisher/BrokerSubscriber）。
     */
    public TdmqMQClient(TdmqProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 双构造 2：注入外部 BrokerPublisher / BrokerSubscriber（runtime 集成时由业务 SDK 包装传入）。
     */
    public TdmqMQClient(BrokerPublisher publisher, BrokerSubscriber subscriber, TdmqProperties properties) {
        this.brokerPublisher = publisher;
        this.brokerSubscriber = subscriber;
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 注入 TDMQ 业务侧发布器（如腾讯云 SDK 包装）。
     */
    public void setBrokerPublisher(BrokerPublisher publisher) {
        this.brokerPublisher = publisher;
    }

    /**
     * 注入 TDMQ 业务侧订阅器（如腾讯云 SDK 包装）。
     */
    public void setBrokerSubscriber(BrokerSubscriber subscriber) {
        this.brokerSubscriber = subscriber;
    }

    @Override
    public String impl() {
        return "tdmq";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        if (Objects.isNull(brokerPublisher)) {
            // 默认内存总线实现（仅本地联调）：未注入业务侧 publisher 时用内存总线
            this.brokerPublisher = new InMemoryBrokerPublisher(topicSubscribers);
            log.warn("TdmqMQClient: no BrokerPublisher injected, falling back to in-memory broker (test only).");
        }
        return event -> publish(event);
    }

    private void publish(MQEvent event) {
        try {
            String topic = resolveTopic(event, null);
            String tag = event.getTag();
            String payload = serialization().serialize(event).toString();
            brokerPublisher.publish(topic, tag,
                    Objects.nonNull(event.getTenantId()) ? event.getTenantId() : "",
                    Objects.nonNull(event.getMsgId()) ? event.getMsgId() : "",
                    payload.getBytes(StandardCharsets.UTF_8));
            logger().info("Publish MQ [{}]: {}", topic, payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Publish TDMQ event failed", ex);
        }
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) {
        if (!properties.isAutoStartConsumers()) {
            log.info("TDMQ listener registration skipped because autoStartConsumers=false");
            return false;
        }
        String topic = resolveTopic(null, listener);
        String tagExpression = listener.getTags();
        String group = resolveGroup(listener);
        if (Objects.isNull(brokerSubscriber)) {
            this.brokerSubscriber = new InMemoryBrokerSubscriber(topicSubscribers);
            log.warn("TdmqMQClient: no BrokerSubscriber injected, falling back to in-memory broker (test only).");
        }
        Subscription subscription = brokerSubscriber.subscribe(topic, tagExpression, group,
                (messageId, correlationId, payload, requeue) ->
                        handleMessage(messageId, correlationId, payload, requeue, listener));
        subscriptions.add(subscription);
        logger().info("Registered TDMQ listener: topic={}, tags={}, group={}", topic, tagExpression, group);
        return true;
    }

    private void handleMessage(String messageId,
                               String correlationId,
                               byte[] payload,
                               java.util.function.Consumer<Boolean> ackCallback,
                               MQListener listener) {
        try {
            String payloadText = Objects.isNull(payload) ? "" : new String(payload, StandardCharsets.UTF_8);
            // tag = 原生 tag 通过 properties 表达（订阅侧只有 tagExpression，命中则取首项 includes）
            String tag = firstInclude(listener.getTags());
            if (!TagMatcher.match(tag, listener.getTags())) {
                return;
            }
            long deliveryTag = Objects.nonNull(correlationId) ? correlationId.hashCode() : 0L;
            TdmqAcknowledgment ack = new TdmqAcknowledgment(messageId, correlationId, deliveryTag, ackCallback);
            MQEvent event = serialization().deserialize(payloadText, listener.payloadType());
            if (Objects.isNull(event)) {
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                ack.ackSingle();
                return;
            }
            // 同步 tenantId/msgId（业务侧 broker 可能已经设置）
            try {
                consume(listener, event, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
                if (!ack.isAcknowledged()) {
                    ack.nack(properties.isRequeueOnError());
                }
            }
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
            ackCallback.accept(properties.isRequeueOnError());
        }
    }

    @Override
    public void start() {
        // TDMQ 订阅在 initConsumer 时已启动
    }

    // ========================= 关闭 =========================

    public void close() {
        for (Subscription subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Exception ex) {
                logger().warn("Close TDMQ subscription failed", ex);
            }
        }
        subscriptions.clear();
        topicSubscribers.clear();
    }

    // ========================= 工具 =========================

    private String resolveTopic(MQEvent event, MQListener listener) {
        if (Objects.nonNull(listener)) {
            String namespace = StrKit.isNotBlank(listener.getNamespace())
                    ? listener.getNamespace()
                    : StrKit.isNotBlank(properties.getNamespace()) ? properties.getNamespace() : "";
            String topic = StrKit.hasText(listener.getTopic()) ? listener.getTopic() : "ddd4j.default.topic";
            return StrKit.hasText(namespace) ? namespace + "." + topic : topic;
        }
        String namespace = Objects.nonNull(event.getNamespace()) && StrKit.hasText(event.getNamespace())
                ? event.getNamespace()
                : StrKit.hasText(properties.getNamespace()) ? properties.getNamespace() : "";
        String topic = StrKit.hasText(event.getTopic()) ? event.getTopic() : "ddd4j.default.topic";
        return StrKit.hasText(namespace) ? namespace + "." + topic : topic;
    }

    private String resolveGroup(MQListener listener) {
        if (StrKit.hasText(listener.getGroup())) {
            return listener.getGroup();
        }
        return properties.getDefaultGroup();
    }

    private static String firstInclude(String expression) {
        if (!StrKit.hasText(expression)) {
            return null;
        }
        for (String token : expression.split("\\|\\|")) {
            String t = token.trim();
            if (!t.isEmpty() && !"*".equals(t) && !t.startsWith("-")) {
                return t;
            }
        }
        return null;
    }

    // ========================= 业务侧适配接口 =========================

    /**
     * TDMQ 业务侧发布器，由 {@link TdmqMQClient} 通过 {@link #setBrokerPublisher} 注入。
     */
    @FunctionalInterface
    public interface BrokerPublisher {

        /**
         * 发布消息。
         *
         * @param topic        物理 topic（含 namespace 前缀）
         * @param tag          tag，可为 null
         * @param tenantId     租户 ID
         * @param key          业务 Key
         * @param payload      序列化字节流
         */
        void publish(String topic, String tag, String tenantId, String key, byte[] payload);
    }

    /**
     * TDMQ 业务侧订阅器，由 {@link TdmqMQClient} 通过 {@link #setBrokerSubscriber} 注入。
     */
    @FunctionalInterface
    public interface BrokerSubscriber {

        /**
         * 订阅。
         *
         * @param topic           物理 topic
         * @param tagExpression   tag 表达式
         * @param group           订阅组
         * @param messageHandler  消息处理器（messageId, correlationId, payload, ackCallback）
         * @return 订阅句柄
         */
        Subscription subscribe(String topic, String tagExpression, String group,
                              MessageHandler messageHandler);
    }

    /**
     * 消息处理器签名。
     */
    @FunctionalInterface
    public interface MessageHandler {

        void onMessage(String messageId, String correlationId, byte[] payload,
                       java.util.function.Consumer<Boolean> ackCallback);
    }

    /**
     * 订阅句柄。
     */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {

        @Override
        void close();
    }

    /**
     * 默认内存发布器（本地联调/测试）：把消息路由到同进程内订阅者。
     */
    public record DeliveredMessage(String messageId, String correlationId, byte[] payload,
                                   java.util.function.Consumer<Boolean> ackCallback) {
    }

    /**
     * 内存发布器实现。
     */
    static final class InMemoryBrokerPublisher implements BrokerPublisher {

        private final java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers;

        InMemoryBrokerPublisher(java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers) {
            this.topicSubscribers = topicSubscribers;
        }

        @Override
        public void publish(String topic, String tag, String tenantId, String key, byte[] payload) {
            java.util.List<Consumer<DeliveredMessage>> entries = topicSubscribers.get(topic);
            if (Objects.isNull(entries) || entries.isEmpty()) {
                return;
            }
            String messageId = key.isEmpty() ? java.util.UUID.randomUUID().toString() : key;
            for (Consumer<DeliveredMessage> entry : entries) {
                entry.accept(new DeliveredMessage(
                        messageId,
                        String.valueOf(System.nanoTime()),
                        payload,
                        ack -> {
                        }));
            }
        }
    }

    /**
     * 内存订阅器实现（仅本地联调）。
     */
    static final class InMemoryBrokerSubscriber implements BrokerSubscriber {

        private final java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers;

        InMemoryBrokerSubscriber(java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers) {
            this.topicSubscribers = topicSubscribers;
        }

        @Override
        public Subscription subscribe(String topic, String tagExpression, String group,
                                      MessageHandler handler) {
            Consumer<DeliveredMessage> consumer = msg ->
                    handler.onMessage(msg.messageId(), msg.correlationId(), msg.payload(), msg.ackCallback());
            java.util.List<Consumer<DeliveredMessage>> entries =
                    topicSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
            entries.add(consumer);
            return () -> {
                java.util.List<Consumer<DeliveredMessage>> list = topicSubscribers.get(topic);
                if (Objects.nonNull(list)) {
                    list.remove(consumer);
                }
            };
        }
    }
}
