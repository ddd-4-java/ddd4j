package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * RabbitMQ 客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>整合发布与消费到单一 {@link MQClient} 实现：
 * <ul>
 *   <li>{@link #initProducer} —— 建 {@link Channel}，返回 {@link Consumer} 发布函数，
 *       {@link MQEvent#publish()} 通过它把消息推送到 broker</li>
 *   <li>{@link #initConsumer} —— queueDeclare + queueBind（按 {@link TagMatcher#findIncludes} 绑定多个 routingKey）
 *       + basicConsume 回调，tag 过滤后调 {@link #consume} 统一消费，传入 {@link RabbitAcknowledgment}</li>
 * </ul>
 *
 * <p>路由键（routingKey）= {@code namespace.topic[.tag]}，分隔符 {@code .}；
 * 队列名（queue）= {@code group.namespace.className.methodName}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class RabbitClient implements MQClient {

    private final RabbitMQProperties properties;
    private final AtomicReference<Connection> connectionRef = new AtomicReference<>();

    public RabbitClient(RabbitMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public String impl() {
        return "rabbit";
    }

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        try {
            Channel channel = connection().createChannel();
            return event -> publish(channel, event, mqProperties);
        } catch (IOException ex) {
            throw new IllegalStateException("Init RabbitMQ producer failed", ex);
        }
    }

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) throws Exception {
        Connection connection = connection();
        Channel channel = connection.createChannel();
        String queue = resolveQueue(listener);
        channel.queueDeclare(queue, properties.isDurable(), false, false, null);
        for (String routingKey : resolveRoutingKeys(listener)) {
            channel.queueBind(queue, properties.getExchange(), routingKey);
        }
        DeliverCallback deliverCallback = (consumerTag, delivery) -> handleMessage(channel, delivery, listener, mqProperties);
        channel.basicConsume(queue, mqProperties.isAutoAck(), deliverCallback, consumerTag -> {
        });
        return true;
    }

    // ========================= 发布 =========================

    private void publish(Channel channel, MQEvent event, MQProperties mqProperties) {
        String routingKey = resolveRoutingKey(event);
        String payload = serialization().serialize(event).toString();
        try {
            channel.basicPublish(properties.getExchange(), routingKey, null, payload.getBytes(StandardCharsets.UTF_8));
            logger().info("Publish MQ [{}]: {}", routingKey, payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Publish RabbitMQ event failed, routingKey=" + routingKey, ex);
        }
    }

    private String resolveRoutingKey(MQEvent event) {
        return resolvePhysical(event.getNamespace(), event.getTopic(), event.getTag(), ".");
    }

    /**
     * 解析三段式物理目的地：{@code namespace[sep]topic[sep]tag}。
     *
     * @param namespace 命名空间（可空）
     * @param topic     主题（空时回退默认值）
     * @param tag       标签（可空）
     * @param sep       分隔符（如 {@code .}）
     * @return 物理目的地字符串
     */
    private static String resolvePhysical(String namespace, String topic, String tag, String sep) {
        String base = io.ddd4j.kit.lang.StrKit.hasText(topic) ? topic : "ddd4j.default.topic";
        if (io.ddd4j.kit.lang.StrKit.hasText(namespace)) {
            base = namespace + sep + base;
        }
        return io.ddd4j.kit.lang.StrKit.hasText(tag) ? base + sep + tag : base;
    }

    // ========================= 消费 =========================

    private void handleMessage(Channel channel, com.rabbitmq.client.Delivery delivery,
                               MQListener listener, MQProperties mqProperties) {
        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        try {
            String tag = delivery.getProperties().getHeaders() != null
                    ? Objects.toString(delivery.getProperties().getHeaders().get(MessageHeaders.HEADER_DESTINATION_TAG), null)
                    : null;
            if (!TagMatcher.match(tag, listener.getTags())) {
                if (!mqProperties.isAutoAck()) {
                    channel.basicAck(deliveryTag, false);
                }
                return;
            }
            String payload = new String(delivery.getBody(), StandardCharsets.UTF_8);
            MQEvent event = serialization().deserialize(payload, listener.payloadType());
            if (Objects.isNull(event)) {
                if (!mqProperties.isAutoAck()) {
                    channel.basicAck(deliveryTag, false);
                }
                logger().warn("Consume MQ [{}] failed: the mqEvent is null", listener.namespaceTopicTags());
                return;
            }
            Acknowledgment ack = new RabbitAcknowledgment(channel, deliveryTag, event.getMsgId(), null);
            consume(listener, event, ack);
            if (!mqProperties.isAutoAck() && !ack.isAcknowledged()) {
                ack.ackSingle();
            }
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
            if (!mqProperties.isAutoAck()) {
                try {
                    channel.basicAck(deliveryTag, false);
                } catch (IOException ignore) {
                }
            }
        }
    }

    private List<String> resolveRoutingKeys(MQListener listener) {
        String namespace = io.ddd4j.kit.lang.StrKit.hasText(listener.getNamespace()) ? listener.getNamespace() : "";
        String topic = Objects.isNull(listener.getTopic()) ? "ddd4j.default.topic" : listener.getTopic();
        String base = io.ddd4j.kit.lang.StrKit.hasText(namespace) ? namespace + "." + topic : topic;
        Set<String> includes = TagMatcher.findIncludes(listener.getTags());
        List<String> routingKeys = new ArrayList<>();
        if (includes.isEmpty()) {
            routingKeys.add(base);
        } else {
            includes.forEach(tag -> routingKeys.add(base + "." + tag));
        }
        return routingKeys;
    }

    private String resolveQueue(MQListener listener) {
        String group = Objects.isNull(listener.getGroup()) || listener.getGroup().isEmpty() ? "ddd4j" : listener.getGroup();
        String namespace = io.ddd4j.kit.lang.StrKit.hasText(listener.getNamespace()) ? listener.getNamespace() : "default";
        String className = Objects.isNull(listener.getMethod())
                ? "Unknown"
                : listener.getMethod().getDeclaringClass().getSimpleName();
        String methodName = Objects.isNull(listener.getMethod()) ? "listen" : listener.getMethod().getName();
        return group + "." + namespace + "." + className + "." + methodName;
    }

    // ========================= RabbitMQ 连接 =========================

    private synchronized Connection connection() {
        Connection c = connectionRef.get();
        if (Objects.isNull(c)) {
            try {
                ConnectionFactory factory = properties.connectionFactory();
                Connection nc = factory.newConnection();
                connectionRef.set(nc);
                c = nc;
            } catch (IOException | TimeoutException ex) {
                throw new IllegalStateException("Open RabbitMQ connection failed", ex);
            }
        }
        return c;
    }
}
