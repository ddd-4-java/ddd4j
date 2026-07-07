package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import com.aliyun.openservices.ons.api.ONSFactory;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 阿里云 ONS 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQClient}：
 * <ul>
 *   <li>{@link #initProducer} —— 创建 ONS {@link Producer}，返回 {@link Consumer<MQEvent>}，
 *       {@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— 创建 ONS 消费者，tag 过滤后调 {@link #consume} 统一消费，
 *       传入 {@link OnsAcknowledgment} 实现不同级别 ack</li>
 * </ul>
 *
 * <p>ONS 消费通过监听器返回的 {@link com.aliyun.openservices.ons.api.Action} 来确认消息；
 * 失败默认 {@link com.aliyun.openservices.ons.api.Action#ReconsumeLater} 触发重试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class OnsClient implements MQClient {

    private final OnsProperties properties;
    private final List<com.aliyun.openservices.ons.api.Consumer> consumers = new CopyOnWriteArrayList<>();
    private volatile Producer producer;
    private volatile boolean producerStarted;

    public OnsClient(OnsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "ons";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            Producer p = ONSFactory.createProducer(properties.sessionProperties(properties.getProducerId()));
            p.start();
            this.producer = p;
            this.producerStarted = true;
            return event -> publish(p, event);
        } catch (Exception ex) {
            throw new IllegalStateException("Init ONS producer failed", ex);
        }
    }

    private void publish(Producer producer, MQEvent event) {
        try {
            String topic = StrKit.hasText(event.getTopic()) ? event.getTopic()
                    : (StrKit.hasText(properties.getTopic()) ? properties.getTopic() : "ddd4j.default.topic");
            String tag = event.getTag();
            Message msg = new Message(topic, tag, event.getMsgId(),
                    serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8));
            msg.setKey(event.getMsgId());
            if (Objects.nonNull(event.getTenantId())) {
                msg.putUserProperties(MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
            }
            producer.send(msg);
            logger().info("Publish MQ [{}]: {}", topic, serialization().serialize(event));
        } catch (Exception ex) {
            throw new IllegalStateException("Publish ONS event failed", ex);
        }
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        String group = StrKit.hasText(listener.getGroup()) ? listener.getGroup() : properties.getConsumerId();
        if (!StrKit.hasText(group)) {
            throw new IllegalStateException("OnsClient requires consumerId or @MQEventListener(group=...)");
        }
        String topic = StrKit.hasText(listener.getTopic()) ? listener.getTopic() : properties.getTopic();
        if (!StrKit.hasText(topic)) {
            throw new IllegalStateException("OnsClient requires topic");
        }
        String tag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        com.aliyun.openservices.ons.api.Consumer consumer = ONSFactory.createConsumer(properties.sessionProperties(group));
        consumer.subscribe(topic, properties.subscriptionExpression(tag),
                (msg, ctx) -> handleMessage(msg, ctx, listener));
        consumer.start();
        consumers.add(consumer);
        return true;
    }

    private com.aliyun.openservices.ons.api.Action handleMessage(Message message,
                                                                com.aliyun.openservices.ons.api.ConsumeContext context,
                                                                MQListener listener) {
        try {
            if (!TagMatcher.match(message.getTag(), listener.getTags())) {
                return com.aliyun.openservices.ons.api.Action.CommitMessage;
            }
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                return com.aliyun.openservices.ons.api.Action.CommitMessage;
            }
            // 同步 event 与原生消息的 tag/msgId/tenantId
            if (Objects.nonNull(message.getTag())) {
                event.setTag(message.getTag());
            }
            if (Objects.nonNull(message.getMsgID())) {
                event.setMsgId(message.getMsgID());
            }
            OnsAcknowledgment ack = new OnsAcknowledgment(context, message);
            try {
                consume(listener, event, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
                return com.aliyun.openservices.ons.api.Action.ReconsumeLater;
            }
            return ack.action();
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
            return com.aliyun.openservices.ons.api.Action.ReconsumeLater;
        }
    }

    @Override
    public void start() {
        // ONS producer/consumer 在各自创建时已启动；此方法留作 future 扩展。
    }

    // ========================= 关闭 =========================

    public void close() {
        for (com.aliyun.openservices.ons.api.Consumer c : new ArrayList<>(consumers)) {
            try {
                c.shutdown();
            } catch (Exception ex) {
                logger().warn("Shutdown ONS consumer failed", ex);
            }
        }
        consumers.clear();
        if (producerStarted && Objects.nonNull(producer)) {
            try {
                producer.shutdown();
            } catch (Exception ex) {
                logger().warn("Shutdown ONS producer failed", ex);
            }
            producerStarted = false;
        }
    }
}
