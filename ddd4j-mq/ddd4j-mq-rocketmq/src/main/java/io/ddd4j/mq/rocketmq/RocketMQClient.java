package io.ddd4j.mq.rocketmq;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * RocketMQ 客户端实现（对齐 base-mq RocketClient，纯 Java 零 Spring 依赖）。
 *
 * <p>命名 {@code RocketMQClient}。
 *
 * <p>双构造：
 * <ul>
 *   <li>{@link #RocketMQClient(DefaultMQProducer)} —— 注入已初始化的原生 producer（runtime 自动装配用）</li>
 *   <li>{@link #RocketMQClient(RocketMQProperties)} —— 自行根据 properties 构造 producer（lazy）</li>
 * </ul>
 *
 * <p>借鉴 Kafka：
 * <ul>
 *   <li>partition key：producer 按 {@link MessageQueueSelector} 路由，同 key 进同 queue 保证顺序</li>
 *   <li>producer send 异步 callback（非阻塞发送）</li>
 *   <li>tag 过滤走 RocketMQ 原生 subscribe 表达式（{@code tagA || tagB}），应用层 TagMatcher 兜底 excludes</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : rocketMQClient ###")
public class RocketMQClient implements MQClient {

    /**
     * 按 partition key 哈希选 MessageQueue（同 key 必进同 queue）。
     */
    private static final MessageQueueSelector SELECTOR_BY_KEY = (mqs, msg, arg) -> {
        String key = Objects.toString(arg, "");
        int index = Math.abs(key.hashCode()) % mqs.size();
        return mqs.get(index);
    };
    /**
     * 懒构造使用的配置（构造方法 2 传入）
     */
    private final RocketMQProperties properties;
    /**
     * 已注入或懒构造的 RocketMQ producer
     */
    private volatile DefaultMQProducer producer;
    /**
     * 异步发送回调（可为 null，则用内置兜底）。
     */
    private SendCallback callback;

    /**
     * \u6784\u9020\u65b9\u6cd5 1\uff1a\u6ce8\u5165\u539f\u751f producer\uff08runtime \u81ea\u52a8\u88c5\u914d\u7528\uff09\u3002
     *
     * @param producer \u5df2\u521d\u59cb\u5316\u7684\u539f\u751f\u751f\u4ea7\u8005\uff0c\u4e0d\u53ef\u4e3a\u7a7a
     */
    public RocketMQClient(DefaultMQProducer producer) {
        this.producer = Objects.requireNonNull(producer, "RocketMQ Producer is required");
        this.properties = null;
    }

    /**
     * \u6784\u9020\u65b9\u6cd5 1'\uff1a\u6ce8\u5165\u539f\u751f producer + \u5f02\u6b65\u53d1\u9001\u56de\u8c03\u3002
     *
     * @param producer \u5df2\u521d\u59cb\u5316\u7684\u539f\u751f\u751f\u4ea7\u8005\uff0c\u4e0d\u53ef\u4e3a\u7a7a
     * @param callback \u5f02\u6b65\u53d1\u9001\u56de\u8c03\uff0c\u53ef\u4e3a\u7a7a\uff08\u4e3a\u7a7a\u65f6\u4ec5\u8bb0\u5f55\u53d1\u9001\u7ed3\u679c\u65e5\u5fd7\uff09
     */
    public RocketMQClient(DefaultMQProducer producer, SendCallback callback) {
        this.producer = Objects.requireNonNull(producer, "RocketMQ Producer is required");
        this.properties = null;
        this.callback = callback;
    }

    /**
     * \u6784\u9020\u65b9\u6cd5 2\uff1a\u81ea\u884c\u6839\u636e properties \u6784\u9020 producer\uff08lazy\uff09\u3002
     *
     * @param properties \u9a71\u52a8\u61d2\u6784\u9020\u7684\u914d\u7f6e\uff0c\u4e0d\u53ef\u4e3a\u7a7a
     */
    public RocketMQClient(RocketMQProperties properties) {
        this.producer = null;
        this.properties = Objects.requireNonNull(properties, "RocketMQ Properties is required");
    }

    /**
     * \u6784\u9020\u65b9\u6cd5 2'\uff1a\u81ea\u884c\u6839\u636e properties \u6784\u9020 producer + \u5f02\u6b65\u53d1\u9001\u56de\u8c03\uff08lazy\uff09\u3002
     *
     * @param properties \u9a71\u52a8\u61d2\u6784\u9020\u7684\u914d\u7f6e\uff0c\u4e0d\u53ef\u4e3a\u7a7a
     * @param callback \u5f02\u6b65\u53d1\u9001\u56de\u8c03\uff0c\u53ef\u4e3a\u7a7a\uff08\u4e3a\u7a7a\u65f6\u4ec5\u8bb0\u5f55\u53d1\u9001\u7ed3\u679c\u65e5\u5fd7\uff09
     */
    public RocketMQClient(RocketMQProperties properties, SendCallback callback) {
        this.producer = null;
        this.properties = Objects.requireNonNull(properties, "RocketMQ Properties is required");
        this.callback = callback;
    }

    // ========================= 生产者 =========================

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"rocket"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "rocket";
    }

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>RocketMQ 实现调用 <code>this.properties.getUsername()</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>null</code>
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            if (Objects.isNull(producer) && Objects.nonNull(this.properties)) {
                DefaultMQProducer p;
                String username = this.properties.getUsername();
                String password = this.properties.getPassword();
                if (StrKit.isNotEmpty(username) && StrKit.isNotEmpty(password)) {
                    p = new DefaultMQProducer(this.properties.getProducerGroup(),
                            new AclClientRPCHook(new SessionCredentials(username, password)));
                } else {
                    p = new DefaultMQProducer(this.properties.getProducerGroup());
                }
                if (StrKit.isNotEmpty(this.properties.getNameServer())) {
                    p.setNamesrvAddr(this.properties.getNameServer());
                }
                p.start();
                this.producer = p;
                log.info("Init RocketMQ producer with {}", this.properties);
            }
            DefaultMQProducer finalProducer = this.producer;
            return event -> {
                String payload = serialization().serialize(event);
                String topic = resolveTopic(event, mqProperties);
                String tag = Objects.isNull(event.getTag()) ? "" : event.getTag();
                try {
                    Message msg = new Message(event.getTopic(), tag, payload.getBytes(StandardCharsets.UTF_8));
                    // tag header 与 broker 端 selector property name 一致（无 .，合法标识符）
                    if (Objects.nonNull(event.getTag())) {
                        msg.putUserProperty(tagHeaderKey(), event.getTag());
                    }
                    if (Objects.nonNull(event.getTenantId())) {
                        msg.putUserProperty("ddd4jTenantId", event.getTenantId());
                    }
                    if (StrKit.isNotEmpty(event.getMsgId())) {
                        msg.putUserProperty(MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
                    }
                    String key = partitionKey(event);
                    if (StrKit.isEmpty(key)) {
                        finalProducer.send(msg, Objects.nonNull(callback) ? callback : new SendLogCallback(topic, payload));
                    } else {
                        // 同 key 进同 queue：MessageQueueSelector 按 key 哈希选 queue，保证顺序
                        finalProducer.send(msg, SELECTOR_BY_KEY, key,
                                Objects.nonNull(callback) ? callback : new SendLogCallback(topic, payload));
                    }
                    log.info("Publish MQ [{}]: {}", topic, payload);
                } catch (Exception e) {
                    log.error("Publish MQ [{}]: {} failed!", topic, payload, e);
                }
            };
        } catch (MQClientException e) {
            log.error("Init RocketMQ producer failed", e);
            return null;
        }
    }

    /**
     * \u6839\u636e {@link PartitionKeyStrategy} \u679a\u4e3e\u8ba1\u7b97 partition key\uff08\u4eff KafkaMQClient\uff09\u3002
     *  <p>RocketMQ \u501f {@link MessageQueueSelector} \u4fdd\u8bc1\u540c key \u8fdb\u540c MessageQueue \u2192 \u540c tag / \u540c\u79df\u6237\u6d88\u606f\u4e25\u683c\u987a\u5e8f\u3002
     *  <p>TAG_TENANT/TAG/TENANT \u4e09\u79cd\u590d\u7528\u7236\u7c7b\u9ed8\u8ba4\u884c\u4e3a\uff1bNONE \u8fd4\u56de null\uff1b
     *  CUSTOM \u65f6\u5e94\u7531\u5b50\u7c7b\u8986\u5199 {@link #partitionKey(MQEvent)} \u81ea\u5b9a\u4e49\u3002
     *
     * @param event \u51b3\u5b9a\u6d88\u606f\u8def\u7531\u7684\u53d1\u5e03\u4e8b\u4ef6
     * @return \u53d1\u9001\u6d88\u606f\u65f6\u4f7f\u7528\u7684\u8def\u7531\u952e
     */
    @Override
    public String partitionKey(MQEvent event) {
        if (Objects.isNull(properties)) {
            return MQClient.super.partitionKey(event);  // 双构造 1：注入 producer 时走父类默认
        }
        return switch (properties.getPartitionKeyStrategy()) {
            case NONE -> null;
            case TAG -> Objects.nonNull(event) ? event.getTag() : null;
            case TENANT -> Objects.nonNull(event) ? event.getTenantId() : null;
            case TAG_TENANT -> MQClient.super.partitionKey(event);
            case CUSTOM -> MQClient.super.partitionKey(event);  // 占位：子类应自己覆写
        };
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>RocketMQ 实现调用 <code>this.properties.newConsumer(listener.getGroup())</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        DefaultMQPushConsumer consumer;
        if (Objects.nonNull(this.properties)) {
            consumer = this.properties.newConsumer(listener.getGroup());
            String username = this.properties.getUsername();
            String password = this.properties.getPassword();
            if (StrKit.isNotEmpty(username) && StrKit.isNotEmpty(password)) {
                consumer = new DefaultMQPushConsumer(listener.getGroup(),
                        new AclClientRPCHook(new SessionCredentials(username, password)));
                if (StrKit.isNotEmpty(this.properties.getNameServer())) {
                    consumer.setNamesrvAddr(this.properties.getNameServer());
                }
            }
        } else {
            consumer = new DefaultMQPushConsumer(listener.getGroup());
        }
        // broker 端 tag 过滤：RocketMQ 原生 subscribe(topic, tagExpression) 由 broker 端按表达式过滤；
        // 仅当 tags 含纯正向 include 时下发精确表达式（tagA || tagB），否则回退 * + 应用层 TagMatcher 兜底。
        Set<String> includes = TagMatcher.findIncludes(listener.getTags());
        String subscription = includes.isEmpty() ? "*"
                : includes.stream().collect(Collectors.joining(" || "));
        String topic = resolveTopic(listener, mqProperties);
        consumer.subscribe(listener.getTopic(), subscription);
        consumer.registerMessageListener((MessageListenerConcurrently) (messageExts, context) -> {
            for (MessageExt messageExt : messageExts) {
                String payload = new String(messageExt.getBody(), StandardCharsets.UTF_8);
                MQEvent event;
                try {
                    event = serialization().deserialize(payload, listener.payloadType());
                } catch (Throwable ex) {
                    log.error("Consume MQ [{}] deserialize failed: {}", listener.getRouteExpression(this.defaultConcat()), payload, ex);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                if (Objects.isNull(event)) {
                    log.warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(this.defaultConcat()));
                    continue;
                }
                String messageId = messageId(messageExt);
                if (StrKit.isNotEmpty(messageId)) {
                    event.setMsgId(messageId);
                } else if (StrKit.isEmpty(event.getMsgId()) && StrKit.isNotEmpty(messageExt.getMsgId())) {
                    event.setMsgId(messageExt.getMsgId());
                }
                // 若 subscribe 没做精确过滤（含 excludes/通配），在应用层再用 TagMatcher 兜底
                if (includes.isEmpty() && !TagMatcher.match(event.getTag(), listener.getTags())) {
                    continue;
                }
                RocketAcknowledgment ack = new RocketAcknowledgment(messageExt);
                try {
                    consume(listener, event, ack);
                    if (ack.shouldReconsume()) {
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                    if (!ack.isAcknowledged()) {
                        // RocketMQ 无显式单条 ack，consume 正常返回即视为消费成功
                    }
                } catch (Throwable ex) {
                    log.error("Consume MQ [{}] failed: {}", listener.getRouteExpression(this.defaultConcat()),
                            serialization().serialize(event), ex);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        log.info("Listen MQ [{}]: topic={}, tags={}", impl(), topic, subscription);
        consumer.start();
        return true;
    }

    // ========================= 消费者 =========================

    /**
     * 异步发送回调（统一收口，不阻塞 producer.send()）。
     */
    public static final class SendLogCallback implements SendCallback {

        /** \u6d88\u606f\u6295\u9012\u6216\u8ba2\u9605\u7684\u4e3b\u9898\u3002 */
        private final String topic;
        /** \u6d88\u606f\u8f6c\u79fb\u6216\u53d1\u9001\u65f6\u4f7f\u7528\u7684\u5e8f\u5217\u5316\u8d1f\u8f7d\u3002 */
        private final String payload;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>SendLogCallback</code> \u5b9e\u4f8b\u3002
         * @param topic \u6d88\u606f\u6295\u9012\u6216\u8ba2\u9605\u7684\u4e3b\u9898
         * @param payload <code>payload</code> \u5b57\u6bb5
         */
        SendLogCallback(String topic, String payload) {
            this.topic = topic;
            this.payload = payload;
        }

        /**
         * \u8bb0\u5f55 RocketMQ \u539f\u751f\u53d1\u5e03\u7ed3\u679c\u7528\u4e8e\u8ddf\u8e2a\u53d1\u9001\u3002
         * @param sendResult RocketMQ \u8fd4\u56de\u7684\u539f\u751f\u53d1\u5e03\u7ed3\u679c
         */
        @Override
        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
            if (log.isDebugEnabled()) {
                log.debug("RocketMQ send success: topic={}, msgId={}", topic, sendResult.getMsgId());
            }
        }

        /**
         * \u8bb0\u5f55 RocketMQ \u53d1\u5e03\u5931\u8d25\u53ca\u76f8\u5173\u8bca\u65ad\u4fe1\u606f\u3002
         * @param e \u53d1\u9001\u5931\u8d25\u65f6\u4ee3\u7406\u56de\u8c03\u9644\u5e26\u7684\u5f02\u5e38
         */
        @Override
        public void onException(Throwable e) {
            log.error("RocketMQ send failed: topic={}, payload={}", topic, payload, e);
        }
    }

    /**
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static String messageId(MessageExt message) {
        String messageId = message.getUserProperty(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : message.getUserProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }
}
