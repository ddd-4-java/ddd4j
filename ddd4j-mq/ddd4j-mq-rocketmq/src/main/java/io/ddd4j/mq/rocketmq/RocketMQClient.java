package io.ddd4j.mq.rocketmq;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : rocketMQClient ###")
public class RocketMQClient implements MQClient {

    /** 已注入或懒构造的 RocketMQ producer */
    private volatile DefaultMQProducer producer;
    /** 懒构造使用的配置（构造方法 2 传入） */
    private final RocketMQProperties properties;

    /** 构造方法 1：注入原生 producer */
    public RocketMQClient(DefaultMQProducer producer) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.properties = null;
    }

    /** 构造方法 2：自行根据 properties 构造 producer（lazy） */
    public RocketMQClient(RocketMQProperties properties) {
        this.producer = null;
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "rocket";
    }

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            if (Objects.isNull(producer)) {
                DefaultMQProducer p;
                String username = properties.getNameServer();
                String password = mqProperties.getPassword();
                if (Objects.nonNull(username) && !username.isEmpty()
                        && Objects.nonNull(password) && !password.isEmpty()) {
                    p = new DefaultMQProducer(properties.getProducerGroup(),
                            new AclClientRPCHook(new SessionCredentials(mqProperties.getUsername(), password)));
                } else {
                    p = new DefaultMQProducer(properties.getProducerGroup());
                }
                if (Objects.nonNull(properties.getNameServer()) && !properties.getNameServer().isEmpty()) {
                    p.setNamesrvAddr(properties.getNameServer());
                }
                p.start();
                this.producer = p;
            }
            DefaultMQProducer finalProducer = this.producer;
            return mqEvent -> {
                String message = serialization().serialize(mqEvent);
                String tags = mqEvent.getTag() == null ? "" : mqEvent.getTag();
                try {
                    finalProducer.send(new Message(mqEvent.getTopic(), tags,
                            message.getBytes(RemotingHelper.DEFAULT_CHARSET)));
                    log.info("Publish MQ [{}]: {}", mqEvent.getTopic() + "." + tags, message);
                } catch (Exception e) {
                    log.error("Publish MQ [{}]: {} failed!", mqEvent.getTopic() + "." + tags, message, e);
                }
            };
        } catch (MQClientException e) {
            log.error("Init RocketMQ producer failed", e);
            return null;
        }
    }

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        DefaultMQPushConsumer consumer;
        String username = mqProperties.getUsername();
        String password = mqProperties.getPassword();
        if (Objects.nonNull(username) && !username.isEmpty()
                && Objects.nonNull(password) && !password.isEmpty()) {
            consumer = new DefaultMQPushConsumer(listener.getGroup(),
                    new AclClientRPCHook(new SessionCredentials(username, password)));
        } else {
            consumer = new DefaultMQPushConsumer(listener.getGroup());
        }
        if (Objects.nonNull(properties.getNameServer()) && !properties.getNameServer().isEmpty()) {
            consumer.setNamesrvAddr(properties.getNameServer());
        }
        consumer.subscribe(listener.getTopic(), subscriptionExpression(listener.getTags()));
        consumer.registerMessageListener((MessageListenerConcurrently) (messageExts, context) -> {
            for (MessageExt messageExt : messageExts) {
                String tag = messageExt.getTags();
                if (!TagMatcher.match(tag, listener.getTags())) {
                    continue;
                }
                String payload = new String(messageExt.getBody(), StandardCharsets.UTF_8);
                MQEvent event;
                try {
                    event = serialization().deserialize(payload, listener.payloadType());
                } catch (Throwable ex) {
                    log.error("Consume MQ [{}] deserialize failed: {}", listener.namespaceTopicTags(), payload, ex);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                if (Objects.isNull(event)) {
                    log.warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                    continue;
                }
                RocketAcknowledgment ack = new RocketAcknowledgment(messageExt);
                try {
                    consume(listener, event, ack);
                    if (ack.shouldReconsume()) {
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                } catch (Throwable ex) {
                    log.error("Consume MQ [{}] failed: {}", listener.namespaceTopicTags(),
                            serialization().serialize(event), ex);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        return true;
    }

    /**
     * RocketMQ 订阅表达式：空或含 {@code -} 用通配 {@code *}；否则原样下发。
     */
    private static String subscriptionExpression(String tags) {
        if (!io.ddd4j.kit.lang.StrKit.hasText(tags) || tags.contains("-")) {
            return "*";
        }
        return tags;
    }
}