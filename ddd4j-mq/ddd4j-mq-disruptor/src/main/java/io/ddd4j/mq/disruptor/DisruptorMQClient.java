package io.ddd4j.mq.disruptor;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.util.TagMatcher;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * LMAX Disruptor（进程内 MQ）客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQClient}：
 * <ul>
 *   <li>{@link #initProducer(MQProperties)} —— 返回 {@link Consumer<MQEvent>}，
 *       {@link MQEvent#publish()} 通过它把消息推进 RingBuffer</li>
 *   <li>{@link #initConsumer(MQListener, MQProperties)} —— 按 routeKey 注册监听器，
 *       RingBuffer 事件到达后反序列化 → 构建 {@link DisruptorAcknowledgment} →
 *       调 {@link MQClient#consume(MQListener, MQEvent, Acknowledgment)} 完成统一消费</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class DisruptorMQClient implements MQClient {

    private final DisruptorMQProperties properties;
    private final Map<String, List<RegisteredListener>> handlersByRoute = new ConcurrentHashMap<>();
    private DisruptorMQBus bus;

    public DisruptorMQClient(DisruptorMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.bus = new DisruptorMQBus(properties, this::dispatch);
    }

    @Override
    public String impl() {
        return "disruptor";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        return event -> publish(event, mqProperties);
    }

    private void publish(MQEvent event, MQProperties mqProperties) {
        String namespace = Objects.nonNull(event.getNamespace())
                ? event.getNamespace()
                : (Objects.nonNull(mqProperties) ? mqProperties.getNamespace() : null);
        String topic = Objects.nonNull(event.getTopic())
                ? event.getTopic()
                : (Objects.nonNull(mqProperties) ? mqProperties.getDefaultTopic() : "DEFAULT");
        String tag = event.getTag();
        String payload = serialization().serialize(event).toString();
        bus.publish(namespace, topic, tag, event.getMsgId(), payload);
        logger().info("Publish MQ [{}]: {}", topic, payload);
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) {
        Objects.requireNonNull(listener, "listener");
        String routeKey = buildRouteKey(listener);
        handlersByRoute
                .computeIfAbsent(routeKey, key -> new CopyOnWriteArrayList<>())
                .add(new RegisteredListener(listener, mqProperties));
        logger().info("Registered Disruptor consumer: routeKey={}, bean={}, method={}",
                routeKey,
                listener.getMethod().getDeclaringClass().getSimpleName(),
                listener.getMethod().getName());
        return true;
    }

    /**
     * RingBuffer 事件回调入口（{@link DisruptorMQBus} 在启动时注入）。
     */
    private void dispatch(DisruptorMQEvent event) {
        if (Objects.isNull(event) || Objects.isNull(event.getTopic())) {
            return;
        }
        String routeKey = event.routeKey();
        List<RegisteredListener> handlers = handlersByRoute.get(routeKey);
        if (Objects.isNull(handlers) || handlers.isEmpty()) {
            logger().trace("No Disruptor handler for routeKey={}", routeKey);
            event.clear();
            return;
        }
        for (RegisteredListener registered : handlers) {
            handleMessage(registered, event);
        }
        event.clear();
    }

    private void handleMessage(RegisteredListener registered, DisruptorMQEvent event) {
        MQListener listener = registered.listener();
        try {
            // TagMatcher 过滤（RingBuffer 已按 routeKey 匹配 namespace.topic.tag）
            if (!TagMatcher.match(event.getTag(), listener.getTags())) {
                return;
            }
            MQEvent mqEvent = serialization().deserialize(event.getPayload(), listener.payloadType());
            Acknowledgment ack = new DisruptorAcknowledgment(event, bus.getRingBuffer(), event.getSequence());
            consume(listener, mqEvent, ack);
            if (!ack.isAcknowledged()) {
                ack.ackSingle();
            }
        } catch (Throwable ex) {
            logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
        }
    }

    @Override
    public void start() {
        // RingBuffer 在构造时已 start，留空实现对齐 MQClient.start() 契约。
    }

    /**
     * 关闭底层 Disruptor（与 Spring 适配层解耦时也可手动调用）。
     */
    public void shutdown() {
        if (Objects.nonNull(bus)) {
            bus.shutdown();
        }
    }

    /**
     * 根据监听器定义构建 routeKey：{@code namespace.topic[.tag]}，tag 取第一个正向 include。
     */
    private static String buildRouteKey(MQListener listener) {
        String namespace = listener.getNamespace();
        String topic = listener.getTopic();
        String tag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        DisruptorMQEvent probe = new DisruptorMQEvent();
        probe.setNamespace(namespace);
        probe.setTopic(topic);
        probe.setTag(tag);
        return probe.routeKey();
    }

    /**
     * 已注册监听器记录。
     */
    private record RegisteredListener(MQListener listener, MQProperties mqProperties) {
    }
}
