package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 阿里云 ONS 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer} 与 {@link #initConsumer}，核心业务逻辑全部内联。
 *
 * <p>ONS 是阿里云 RocketMQ，提供原生 subscription 表达式 tag 过滤。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : OnsMQClient ###")
public class OnsMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final OnsProperties properties;
    /** \u5f53\u524d\u9002\u914d\u5668\u5df2\u5efa\u7acb\u7684\u539f\u751f\u6d88\u8d39\u8005\u3002 */
    private final List<com.aliyun.openservices.ons.api.Consumer> consumers = new CopyOnWriteArrayList<>();
    /** 保存 <code>producer</code> 对应的 <code>Producer</code> 数据。 */
    private volatile Producer producer;
    /** 保存 <code>producer Started</code> 对应的 <code>boolean</code> 数据。 */
    private volatile boolean producerStarted;

    /**
     * 构造 1：传入配置，{@link #initProducer}/{@link #initConsumer} 中通过 ONSFactory 创建原生客户端。
     *
     * @param properties ONS 配置
     */
    public OnsMQClient(OnsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 构造 2：注入已初始化的原生 ONS {@link Producer}（用于 runtime 集成自动注入）。
     * 注：ONS 消费者侧仍需 {@link OnsProperties} 通过 ONSFactory 创建，因此构造 2 同时接受
     * {@link OnsProperties}（供消费者侧使用）。
     *
     * @param producer   原生 ONS producer（运行时构造预创建）
     * @param properties ONS 配置（消费者侧使用）
     */
    public OnsMQClient(Producer producer, OnsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.producer = producer;
    }

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"ons"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "ons";
    }

    // ========================= 生产者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * <p>此实现调用 <code>ONSFactory.createProducer(properties.sessionProperties(properties.getProducerId()))</code>。</p>
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            Producer p = Objects.nonNull(producer) ? producer
                    : ONSFactory.createProducer(properties.sessionProperties(properties.getProducerId()));
            p.start();
            this.producer = p;
            this.producerStarted = true;
            return event -> {
                try {
                    String topic = resolveTopic(event, mqProperties);
                    String tag = event.getTag();
                    Message msg = new Message(topic, tag, event.getMsgId(),
                            serialization().serialize(event).toString().getBytes(StandardCharsets.UTF_8));
                    msg.setKey(event.getMsgId());
                    if (StrKit.isNotEmpty(event.getMsgId())) {
                        msg.putUserProperties(MessageHeaders.HEADER_MESSAGE_ID, event.getMsgId());
                    }
                    if (Objects.nonNull(event.getTenantId())) {
                        msg.putUserProperties(MessageHeaders.HEADER_TENANT_ID, event.getTenantId());
                    }
                    p.send(msg);
                    logger().info("Publish MQ [{}]: {}", topic, serialization().serialize(event));
                } catch (Exception ex) {
                    throw new IllegalStateException("Publish ONS event failed", ex);
                }
            };
        } catch (Exception ex) {
            throw new IllegalStateException("Init ONS producer failed", ex);
        }
    }

    // ========================= 消费者 =========================

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>StrKit.hasText(listener.getGroup())</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
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
        // ONS 支持 subscription 表达式原生 tag 过滤
        String tagExpression = properties.subscriptionExpression(
                TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null));
        com.aliyun.openservices.ons.api.Consumer consumer = ONSFactory.createConsumer(properties.sessionProperties(group));
        consumer.subscribe(topic, tagExpression, (msg, ctx) -> {
            try {
                if (!TagMatcher.match(msg.getTag(), listener.getTags())) {
                    return com.aliyun.openservices.ons.api.Action.CommitMessage;
                }
                String payload = new String(msg.getBody(), StandardCharsets.UTF_8);
                MQEvent event = serialization().deserialize(payload, listener.payloadType());
                if (Objects.isNull(event)) {
                    logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.getRouteExpression(this.defaultConcat()));
                    return com.aliyun.openservices.ons.api.Action.CommitMessage;
                }
                if (Objects.nonNull(msg.getTag())) {
                    event.setTag(msg.getTag());
                }
                String messageId = messageId(msg);
                if (StrKit.isNotEmpty(messageId)) {
                    event.setMsgId(messageId);
                } else if (StrKit.isEmpty(event.getMsgId()) && StrKit.isNotEmpty(msg.getMsgID())) {
                    event.setMsgId(msg.getMsgID());
                }
                OnsAcknowledgment ack = new OnsAcknowledgment(ctx, msg);
                try {
                    consume(listener, event, ack);
                    if (!ack.isAcknowledged()) {
                        ack.ackSingle();
                    }
                } catch (Throwable ex) {
                    logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                    return com.aliyun.openservices.ons.api.Action.ReconsumeLater;
                }
                return ack.action();
            } catch (Throwable ex) {
                logger().error("Consume MQ [{}] failed", listener.getRouteExpression(this.defaultConcat()), ex);
                return com.aliyun.openservices.ons.api.Action.ReconsumeLater;
            }
        });
        consumer.start();
        consumers.add(consumer);
        return true;
    }

    /** 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。 */
    @Override
    public void start() {
        // ONS producer/consumer 在各自创建时已启动；此方法留作 future 扩展。
    }

    // ========================= 关闭 =========================

    /** \u91ca\u653e\u6d88\u606f\u5ba2\u6237\u7aef\u62e5\u6709\u7684\u8fd0\u884c\u8d44\u6e90\uff0c\u5e76\u5728\u9000\u51fa\u65f6\u767b\u8bb0\u7ec4\u4ef6\u5df2\u505c\u6b62\u3002 */
    @Override
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

    /**
     * \u4f18\u5148\u4ece\u539f\u751f\u6d88\u606f\u7684\u6807\u51c6\u5934\u8bfb\u53d6\u6d88\u606f\u6807\u8bc6\uff0c\u7f3a\u5c11\u65f6\u56de\u9000\u5230\u517c\u5bb9\u5934\u3002
     * @param message \u5f53\u524d\u63a5\u6536\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f
     * @return \u7528\u4e8e\u540e\u7eed\u6d88\u8d39\u786e\u8ba4\u7684\u4e1a\u52a1\u6d88\u606f\u6807\u8bc6\uff0c\u5934\u5747\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static String messageId(Message message) {
        String messageId = message.getUserProperties(MessageHeaders.HEADER_MESSAGE_ID);
        return StrKit.isNotEmpty(messageId)
                ? messageId
                : message.getUserProperties(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
    }
}
