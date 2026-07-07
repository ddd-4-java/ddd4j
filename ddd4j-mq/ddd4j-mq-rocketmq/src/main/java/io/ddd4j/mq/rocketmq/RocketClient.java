package io.ddd4j.mq.rocketmq;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * RocketMQ 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>整合发布与消费到单一 {@link MQClient} 实现：
 * <ul>
 *   <li>{@link #initProducer} —— 创建 {@link DefaultMQProducer}（按需启用 AclClientRPCHook 鉴权），
 *       返回发布函数（core 自动适配为 {@link io.ddd4j.mq.event.MQEventPublisher}）</li>
 *   <li>{@link #initConsumer} —— 创建 {@link DefaultMQPushConsumer}，收到消息后 tag 过滤 → 反序列化 →
 *       构建 {@link RocketAcknowledgment} → 调 {@link #consume} 统一消费</li>
 * </ul>
 *
 * <p>RocketMQ 特殊之处：消费结果通过 listener 返回 {@link ConsumeConcurrentlyStatus} 表达，
 * 消费失败或标记重投时返回 {@link ConsumeConcurrentlyStatus#RECONSUME_LATER} 触发重试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class RocketClient implements MQClient {

    private static final String DEFAULT_CONCAT = ".";

    /**
     * 已注册的消费者，统一在 {@link #start()} 启动。
     */
    private final List<DefaultMQPushConsumer> consumers = new CopyOnWriteArrayList<>();

    @Override
    public String impl() {
        return "rocket";
    }

    // ========================= 发布 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            DefaultMQProducer producer = createProducer(mqProperties);
            producer.start();
            return event -> publish(producer, event, mqProperties);
        } catch (MQClientException ex) {
            throw new java.lang.IllegalStateException("Init RocketMQ producer failed", ex);
        }
    }

    private void publish(DefaultMQProducer producer, MQEvent event, MQProperties mqProperties) {
        try {
            String topic = resolveTopic(event.getNamespace(), event.getTopic(), mqProperties);
            String tag = StrKit.hasText(event.getTag()) ? event.getTag() : "";
            String payload = serialization().serialize(event).toString();
            Message message = StrKit.hasText(tag)
                    ? new Message(topic, tag, payload.getBytes(StandardCharsets.UTF_8))
                    : new Message(topic, payload.getBytes(StandardCharsets.UTF_8));
            String keys = StrKit.hasText(event.getMsgId()) ? event.getMsgId() : event.getTenantId();
            if (StrKit.hasText(keys)) {
                message.setKeys(keys);
            }
            putUserProperty(message, MessageHeaders.HEADER_DESTINATION_TOPIC, topic);
            putUserProperty(message, MessageHeaders.HEADER_DESTINATION_TAG, tag);
            putUserProperty(message, MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
            producer.send(message);
            logger().info("Publish MQ [{}]: {}", StrKit.hasText(tag) ? topic + DEFAULT_CONCAT + tag : topic, payload);
        } catch (Exception ex) {
            throw new java.lang.IllegalStateException("Publish RocketMQ event failed", ex);
        }
    }

    // ========================= 消费 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        DefaultMQPushConsumer consumer = createConsumer(resolveGroup(listener), mqProperties);
        consumer.subscribe(resolveTopic(listener), subscriptionExpression(listener.getTags()));
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) ->
                consumeMessages(messages, listener));
        consumers.add(consumer);
        return true;
    }

    private ConsumeConcurrentlyStatus consumeMessages(List<MessageExt> messages, MQListener listener) {
        for (MessageExt message : messages) {
            String tag = message.getTags();
            if (!TagMatcher.match(tag, listener.getTags())) {
                continue;
            }
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                continue;
            }
            RocketAcknowledgment ack = new RocketAcknowledgment(message);
            try {
                consume(listener, event, ack);
                if (ack.shouldReconsume()) {
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            } catch (Throwable ex) {
                logger().error("Consume MQ [{}] failed: {}", listener.namespaceTopicTags(),
                        serialization().serialize(event), ex);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    @Override
    public void start() {
        for (DefaultMQPushConsumer consumer : consumers) {
            try {
                consumer.start();
            } catch (MQClientException ex) {
                logger().error("Start RocketMQ consumer failed", ex);
            }
        }
    }

    // ========================= RocketMQ 客户端构建 =========================

    private DefaultMQProducer createProducer(MQProperties mqProperties) {
        String username = mqProperties.getUsername();
        String password = mqProperties.getPassword();
        String producerGroup = StrKit.hasText(mqProperties.getProducerGroup())
                ? mqProperties.getProducerGroup() : "DEFAULT_PRODUCER";
        DefaultMQProducer producer = hasAuth(username, password)
                ? new DefaultMQProducer(producerGroup, new AclClientRPCHook(new SessionCredentials(username, password)))
                : new DefaultMQProducer(producerGroup);
        if (StrKit.hasText(mqProperties.getServer())) {
            producer.setNamesrvAddr(mqProperties.getServer());
        }
        return producer;
    }

    private DefaultMQPushConsumer createConsumer(String group, MQProperties mqProperties) {
        String username = mqProperties.getUsername();
        String password = mqProperties.getPassword();
        DefaultMQPushConsumer consumer = hasAuth(username, password)
                ? new DefaultMQPushConsumer(group, new AclClientRPCHook(new SessionCredentials(username, password)))
                : new DefaultMQPushConsumer(group);
        if (StrKit.hasText(mqProperties.getServer())) {
            consumer.setNamesrvAddr(mqProperties.getServer());
        }
        return consumer;
    }

    private static boolean hasAuth(String username, String password) {
        return StrKit.hasText(username) && StrKit.hasText(password);
    }

    // ========================= 目的地解析 =========================

    private String resolveTopic(String namespace, String topic, MQProperties mqProperties) {
        String resolvedTopic = StrKit.hasText(topic) ? topic : mqProperties.getDefaultTopic();
        String resolvedNamespace = StrKit.hasText(namespace) ? namespace : mqProperties.getNamespace();
        return StrKit.hasText(resolvedNamespace) ? resolvedNamespace + DEFAULT_CONCAT + resolvedTopic : resolvedTopic;
    }

    private String resolveTopic(MQListener listener) {
        String topic = StrKit.hasText(listener.getTopic()) ? listener.getTopic() : "ddd4j.default.topic";
        String namespace = listener.getNamespace();
        return StrKit.hasText(namespace) ? namespace + DEFAULT_CONCAT + topic : topic;
    }

    private String resolveGroup(MQListener listener) {
        if (StrKit.hasText(listener.getGroup())) {
            return listener.getGroup();
        }
        String topic = StrKit.hasText(listener.getTopic()) ? listener.getTopic() : "default";
        return "ddd4j-" + topic;
    }

    /**
     * RocketMQ 订阅表达式：排除语义（含 {@code -}）或空表达式用通配 {@code *}。
     */
    private static String subscriptionExpression(String tags) {
        if (!StrKit.hasText(tags) || tags.contains("-")) {
            return "*";
        }
        return tags;
    }

    private static void putUserProperty(Message message, String key, String value) {
        if (StrKit.hasText(value)) {
            try {
                message.putUserProperty(key, value);
            } catch (Exception ignore) {
                // putUserProperty 校验 key 合法性，忽略不可写 header
            }
        }
    }
}
