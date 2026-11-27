package io.ddd4j.mq.tdmq;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 腾讯云 TDMQ 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>由于 ddd4j-mq-tdmq 不直接依赖腾讯云官方 SDK（保持无依赖、零 Spring），
 * 实际的 publish/subscribe 由业务侧通过 {@link BrokerPublisher} / {@link BrokerSubscriber} 注入。
 * 该 SPI 当前没有 metadata 参数，稳定消息 ID 只保留在序列化的 {@link MQEvent} payload；不得把
 * 供应商的 transport message id 冒充为 {@code ddd4j-message-id}。
 * 当未注入时，{@link TdmqMQClient} 提供"内存总线"实现，仅供本地联调/测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : TdmqMQClient ###")
public class TdmqMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final TdmqProperties properties;
    /** \u5f53\u524d TDMQ \u5ba2\u6237\u7aef\u7ef4\u62a4\u7684\u6d88\u8d39\u8ba2\u9605\u3002 */
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    /** \u6309\u6d88\u606f\u4e3b\u9898\u8bb0\u5f55\u5f53\u524d\u8ba2\u9605\u53ca\u5176\u76d1\u542c\u5668\u3002 */
    private final java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** \u6d4b\u8bd5\u6216\u81ea\u5b9a\u4e49\u4ee3\u7406\u7684\u6d88\u606f\u53d1\u5e03\u5165\u53e3\u3002 */
    private BrokerPublisher brokerPublisher;
    /** \u6d4b\u8bd5\u6216\u81ea\u5b9a\u4e49\u4ee3\u7406\u7684\u6d88\u606f\u8ba2\u9605\u5165\u53e3\u3002 */
    private BrokerSubscriber brokerSubscriber;

    /**
     * \u6784\u9020 1\uff1a\u4ec5 properties\uff08\u4e1a\u52a1\u53ef\u5728 initProducer/initConsumer \u4e4b\u524d\u6ce8\u5165 BrokerPublisher/BrokerSubscriber\uff09\u3002
     *
     * @param properties TDMQ \u8fde\u63a5\u914d\u7f6e
     */
    public TdmqMQClient(TdmqProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * \u6784\u9020 2\uff1a\u6ce8\u5165\u5916\u90e8 BrokerPublisher / BrokerSubscriber\uff08runtime \u96c6\u6210\u65f6\u7531\u4e1a\u52a1 SDK \u5305\u88c5\u4f20\u5165\uff09\u3002
     *
     * @param publisher \u4e1a\u52a1\u4fa7\u53d1\u5e03\u5668
     * @param subscriber \u4e1a\u52a1\u4fa7\u8ba2\u9605\u5668
     * @param properties TDMQ \u8fde\u63a5\u914d\u7f6e
     */
    public TdmqMQClient(BrokerPublisher publisher, BrokerSubscriber subscriber, TdmqProperties properties) {
        this.brokerPublisher = publisher;
        this.brokerSubscriber = subscriber;
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * \u6ce8\u5165 TDMQ \u4e1a\u52a1\u4fa7\u53d1\u5e03\u5668\uff08\u5982\u817e\u8baf\u4e91 SDK \u5305\u88c5\uff09\u3002
     *
     * @param publisher \u4e1a\u52a1\u4fa7\u53d1\u5e03\u5668
     */
    public void setBrokerPublisher(BrokerPublisher publisher) {
        this.brokerPublisher = publisher;
    }

    /**
     * \u6ce8\u5165 TDMQ \u4e1a\u52a1\u4fa7\u8ba2\u9605\u5668\uff08\u5982\u817e\u8baf\u4e91 SDK \u5305\u88c5\uff09\u3002
     *
     * @param subscriber \u4e1a\u52a1\u4fa7\u8ba2\u9605\u5668
     */
    public void setBrokerSubscriber(BrokerSubscriber subscriber) {
        this.brokerSubscriber = subscriber;
    }

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"tdmq"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "tdmq";
    }

    // ========================= 生产者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>此实现调用 <code>log.warn("TdmqMQClient: no BrokerPublisher injected, falling back to in-memory broker (test only).")</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        if (Objects.isNull(brokerPublisher)) {
            this.brokerPublisher = new InMemoryBrokerPublisher(topicSubscribers);
            log.warn("TdmqMQClient: no BrokerPublisher injected, falling back to in-memory broker (test only).");
        }
        return event -> {
            try {
                String topic = resolveTopic(event, mqProperties);
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
        };
    }

    // ========================= 消费者 =========================

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>properties.isAutoStartConsumers()</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 消费者完成注册时为真
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) {
        if (!properties.isAutoStartConsumers()) {
            log.info("TDMQ listener registration skipped because autoStartConsumers=false");
            return false;
        }
        String topic = resolveTopic(listener, mqProperties);
        String tagExpression = listener.getTags();
        String group = StrKit.hasText(listener.getGroup()) ? listener.getGroup() : properties.getDefaultGroup();
        if (Objects.isNull(brokerSubscriber)) {
            this.brokerSubscriber = new InMemoryBrokerSubscriber(topicSubscribers);
            log.warn("TdmqMQClient: no BrokerSubscriber injected, falling back to in-memory broker (test only).");
        }
        Subscription subscription = brokerSubscriber.subscribe(topic, tagExpression, group,
                (messageId, correlationId, payload, requeue) -> {
                    try {
                        String payloadText = Objects.isNull(payload) ? "" : new String(payload, StandardCharsets.UTF_8);
                        String tag = null;
                        int lastDot = topic.lastIndexOf('.');
                        if (lastDot >= 0) {
                            tag = topic.substring(lastDot + 1);
                        }
                        if (!io.ddd4j.mq.util.TagMatcher.match(tag, listener.getTags())) {
                            return;
                        }
                        long deliveryTag = Objects.nonNull(correlationId) ? correlationId.hashCode() : 0L;
                        TdmqAcknowledgment ack = new TdmqAcknowledgment(messageId, correlationId, deliveryTag, requeue);
                        MQEvent event = serialization().deserialize(payloadText, listener.payloadType());
                        if (Objects.isNull(event)) {
                            logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(this.defaultConcat()));
                            ack.ackSingle();
                            return;
                        }
                        try {
                            consume(listener, event, ack);
                            if (!ack.isAcknowledged()) {
                                ack.ackSingle();
                            }
                        } catch (Throwable ex) {
                            logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                            if (!ack.isAcknowledged()) {
                                ack.nack(properties.isRequeueOnError());
                            }
                        }
                    } catch (Throwable ex) {
                        logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                        requeue.accept(properties.isRequeueOnError());
                    }
                });
        subscriptions.add(subscription);
        logger().info("Registered TDMQ listener: topic={}, tags={}, group={}", topic, tagExpression, group);
        return true;
    }

    /** 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。 */
    @Override
    public void start() {
        // TDMQ 订阅在 initConsumer 时已启动
    }

    // ========================= 关闭 =========================

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
    @Override
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

    // ========================= 业务侧适配接口 =========================

    /**
     * TDMQ 业务侧发布器，由 {@link TdmqMQClient} 通过 {@link #setBrokerPublisher} 注入。
     */
    @FunctionalInterface
    public interface BrokerPublisher {

        /**
         * 发布消息。
         *
         * @param topic    物理 topic（含 namespace 前缀）
         * @param tag      tag，可为 null
         * @param tenantId 租户 ID
         * @param key      业务 Key
         * @param payload  序列化字节流
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
         * @param topic          物理 topic
         * @param tagExpression  tag 表达式
         * @param group          订阅组
         * @param messageHandler 消息处理器（messageId, correlationId, payload, ackCallback）
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

        /**
         * \u5904\u7406\u4e00\u6761\u5df2\u6295\u9012\u6d88\u606f\u3002
         * @param messageId \u6d88\u606f\u6807\u8bc6
         * @param correlationId \u5173\u8054\u6807\u8bc6
         * @param payload \u6d88\u606f\u5185\u5bb9
         * @param ackCallback \u786e\u8ba4\u7ed3\u679c\u56de\u8c03
         */
        void onMessage(String messageId, String correlationId, byte[] payload,
                       java.util.function.Consumer<Boolean> ackCallback);
    }

    /**
     * 订阅句柄。
     */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {

        /** 结束当前组件持有的资源。 */
        @Override
        void close();
    }

    /**
     * 默认内存发布器（本地联调/测试）：把消息路由到同进程内订阅者。
     */
    public final static class DeliveredMessage {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u6d88\u606f\u6807\u8bc6\u3002 */
        private final String messageId;

        /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
        private final String correlationId;

        /** \u6d88\u606f\u8f6c\u79fb\u6216\u53d1\u9001\u65f6\u4f7f\u7528\u7684\u5e8f\u5217\u5316\u8d1f\u8f7d\u3002 */
        private final byte[] payload;

        /** \u539f\u751f\u4ee3\u7406\u66b4\u9732\u7684\u6295\u9012\u786e\u8ba4\u56de\u8c03\u3002 */
        private final java.util.function.Consumer<Boolean> ackCallback;

        /**
         * \u521b\u5efa TDMQ \u5df2\u6295\u9012\u6d88\u606f\u5feb\u7167\u3002
         * @param messageId \u6d88\u606f\u6807\u8bc6
         * @param correlationId \u5173\u8054\u6807\u8bc6
         * @param payload \u6d88\u606f\u5185\u5bb9
         * @param ackCallback \u786e\u8ba4\u7ed3\u679c\u56de\u8c03
         */
        @JsonCreator()
        public DeliveredMessage(@JsonProperty("messageId") String messageId, @JsonProperty("correlationId") String correlationId, @JsonProperty("payload") byte[] payload, @JsonProperty("ackCallback") java.util.function.Consumer<Boolean> ackCallback) {
            this.messageId = messageId;
            this.correlationId = correlationId;
            this.payload = payload;
            this.ackCallback = ackCallback;
        }

        /** @return \u6d88\u606f\u6807\u8bc6 */
        @JsonProperty("messageId")
        public String messageId() {
            return messageId;
        }

        /** @return \u5173\u8054\u6807\u8bc6 */
        @JsonProperty("correlationId")
        public String correlationId() {
            return correlationId;
        }

        /** @return \u6d88\u606f\u5185\u5bb9 */
        @JsonProperty("payload")
        public byte[] payload() {
            return payload;
        }

        /** @return \u786e\u8ba4\u7ed3\u679c\u56de\u8c03 */
        @JsonProperty("ackCallback")
        public java.util.function.Consumer<Boolean> ackCallback() {
            return ackCallback;
        }

        /**
         * 根据<code>messageId</code>、<code>correlationId</code>、<code>payload</code>、<code>ackCallback</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            DeliveredMessage other = (DeliveredMessage) obj;
            return Objects.equals(this.messageId, other.messageId) && Objects.equals(this.correlationId, other.correlationId) && Objects.equals(this.payload, other.payload) && Objects.equals(this.ackCallback, other.ackCallback);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(messageId);
            result = 31 * result + Objects.hashCode(correlationId);
            result = 31 * result + Objects.hashCode(payload);
            result = 31 * result + Objects.hashCode(ackCallback);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>messageId</code>、<code>correlationId</code>、<code>payload</code>、<code>ackCallback</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "DeliveredMessage[messageId=" + messageId + ", correlationId=" + correlationId + ", payload=" + payload + ", ackCallback=" + ackCallback + "]";
        }
    }

    /**
     * 内存发布器实现。
     */
    static final class InMemoryBrokerPublisher implements BrokerPublisher {

        /** \u6309\u6d88\u606f\u4e3b\u9898\u8bb0\u5f55\u5f53\u524d\u8ba2\u9605\u53ca\u5176\u76d1\u542c\u5668\u3002 */
        private final java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers;

        /**
         * \u7528\u5171\u4eab\u8ba2\u9605\u8868\u521b\u5efa\u5185\u5b58\u53d1\u5e03\u7aef\u3002
         * @param topicSubscribers \u6309\u4ee3\u7406\u4e3b\u9898\u4fdd\u5b58\u7684\u5185\u5b58\u8ba2\u9605\u56de\u8c03\u6620\u5c04
         */
        InMemoryBrokerPublisher(java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers) {
            this.topicSubscribers = topicSubscribers;
        }

        /**
         * 将传入的事件交给当前发布通道。
         *
         * <p>此实现调用 <code>topicSubscribers.get(topic)</code>。</p>
         *
         * @param topic 物理 topic（含 namespace 前缀）
         * @param tag tag，可为 null
         * @param tenantId 租户 ID
         * @param key 业务 Key
         * @param payload 序列化字节流
         */
        @Override
        public void publish(String topic, String tag, String tenantId, String key, byte[] payload) {
            java.util.List<Consumer<DeliveredMessage>> entries = topicSubscribers.get(topic);
            if (Objects.isNull(entries) || entries.isEmpty()) {
                return;
            }
            String messageId = StrKit.isEmpty(key) ? java.util.UUID.randomUUID().toString() : key;
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

        /** \u6309\u6d88\u606f\u4e3b\u9898\u8bb0\u5f55\u5f53\u524d\u8ba2\u9605\u53ca\u5176\u76d1\u542c\u5668\u3002 */
        private final java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers;

        /**
         * \u7528\u5171\u4eab\u8ba2\u9605\u8868\u521b\u5efa\u5185\u5b58\u8ba2\u9605\u7aef\u3002
         * @param topicSubscribers \u6309\u4ee3\u7406\u4e3b\u9898\u4fdd\u5b58\u7684\u5185\u5b58\u8ba2\u9605\u56de\u8c03\u6620\u5c04
         */
        InMemoryBrokerSubscriber(java.util.Map<String, java.util.List<Consumer<DeliveredMessage>>> topicSubscribers) {
            this.topicSubscribers = topicSubscribers;
        }

        /**
         * 按给定条件注册消费监听。
         *
         * <p>此实现调用 <code>entries.add(consumer)</code>。</p>
         *
         * @param topic 物理 topic
         * @param tagExpression tag 表达式
         * @param group 订阅组
         * @param handler 容器已匹配的请求处理器
         * @return 订阅句柄
         */
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
