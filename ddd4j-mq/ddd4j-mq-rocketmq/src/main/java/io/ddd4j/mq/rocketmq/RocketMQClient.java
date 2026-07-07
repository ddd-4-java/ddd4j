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
                String topic = resolveTopic(mqEvent, mqProperties);
                String tag = mqEvent.getTag() == null ? "" : mqEvent.getTag();
                try {
                    Message msg = new Message(mqEvent.getTopic(), tag,
                            message.getBytes(RemotingHelper.DEFAULT_CHARSET));
                    // tag header 与 broker 端 selector property name 一致（无 .，合法标识符）
                    if (Objects.nonNull(mqEvent.getTag())) {
                        msg.putUserProperty(tagHeaderKey(), mqEvent.getTag());
                    }
                    finalProducer.send(msg);
                    log.info("Publish MQ [{}]: {}", topic, message);
                } catch (Exception e) {
                    log.error("Publish MQ [{}]: {} failed!", topic, message, e);
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
        // broker 端 tag 过滤：RocketMQ 原生 subscribe(topic, tagsExpression) 由 broker 端按表达式过滤；
        // 仅当 tags 是纯正向 include 集合时下发精确表达式（tagA || tagB），否则回退到应用层 TagMatcher。
        Set<String> includes = TagMatcher.findIncludes(listener.getTags());
        String subscription = includes.isEmpty() ? "*"
                : includes.stream().collect(Collectors.joining(" || "));
        consumer.subscribe(listener.getTopic(), subscription);
        consumer.registerMessageListener((MessageListenerConcurrently) (messageExts, context) -> {
            for (MessageExt messageExt : messageExts) {
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
                // 若 subscribe 没做精确过滤（如含 excludes/通配），在应用层再用 TagMatcher 兜底
                if (includes.isEmpty() && !TagMatcher.match(event.getTag(), listener.getTags())) {
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
}