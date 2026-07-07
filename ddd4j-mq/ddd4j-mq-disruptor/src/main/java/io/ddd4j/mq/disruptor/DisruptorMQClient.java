package io.ddd4j.mq.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * LMAX Disruptor（进程内 MQ）客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>主线只有 {@link #initProducer(MQProperties)} 与 {@link #initConsumer(MQListener, MQProperties)}，
 * 核心业务逻辑全部内联。RingBuffer 生命周期、RingBuffer 事件槽（{@link Event}）作为嵌套类保留在本文件内。
 *
 * <p>模块结构对齐其他 broker：{@code DisruptorMQClient} +
 * {@code DisruptorAcknowledgment} + {@code DisruptorMQProperties} 共 3 个文件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : DisruptorMQClient ###")
public class DisruptorMQClient implements MQClient {

    private final DisruptorMQProperties properties;
    /** 路由 → 监听器列表（同一 topic+namespace+tag 的多个监听器都收到） */
    private final Map<String, List<MQListener>> handlersByRoute = new ConcurrentHashMap<>();
    private Disruptor<Event> disruptor;

    @Getter
    private RingBuffer<Event> ringBuffer;

    /**
     * 双构造 1：传入配置，自建 RingBuffer 与 Disruptor。
     */
    public DisruptorMQClient(DisruptorMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        startRingBuffer();
    }

    /**
     * 双构造 2：传入已初始化的 RingBuffer（runtime 集成复用同一 RingBuffer）。
     */
    public DisruptorMQClient(RingBuffer<Event> ringBuffer) {
        this.properties = new DisruptorMQProperties();
        this.ringBuffer = Objects.requireNonNull(ringBuffer, "ringBuffer");
    }

    @Override
    public String impl() {
        return "disruptor";
    }

    // ========================= 生产者 =========================

    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        return event -> {
            String topic = resolveTopic(event, mqProperties);
            String payload = serialization().serialize(event);
            long sequence = ringBuffer.next();
            try {
                Event e = ringBuffer.get(sequence);
                e.topic = topic;
                e.tag = event.getTag();
                e.namespace = event.getNamespace();
                e.messageId = event.getMsgId();
                e.payload = payload;
                e.sequence = sequence;
            } finally {
                ringBuffer.publish(sequence);
            }
            logger().info("Publish MQ [{}]: {}", topic, payload);
        };
    }

    // ========================= 消费者 =========================

    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) {
        Objects.requireNonNull(listener, "listener");
        String routeKey = resolveRouteKey(listener);
        handlersByRoute
                .computeIfAbsent(routeKey, key -> new CopyOnWriteArrayList<>())
                .add(listener);
        logger().info("Registered Disruptor consumer: routeKey={}, bean={}, method={}",
                routeKey,
                listener.getMethod().getDeclaringClass().getSimpleName(),
                listener.getMethod().getName());
        return true;
    }

    /**
     * RingBuffer 事件回调入口（{@link #startRingBuffer()} 注册到 Disruptor）。
     */
    private void onEvent(Event event, long sequence, boolean endOfBatch) {
        String routeKey = event.routeKey();
        List<MQListener> handlers = handlersByRoute.get(routeKey);
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        for (MQListener listener : handlers) {
            try {
                if (!TagMatcher.match(event.tag, listener.getTags())) {
                    continue;
                }
                MQEvent mqEvent = serialization().deserialize(event.payload, listener.payloadType());
                if (Objects.isNull(mqEvent)) {
                    logger().warn("Disruptor consume [{}] failed: mqEvent is null", listener.namespaceTopicTags());
                    continue;
                }
                DisruptorAcknowledgment ack = new DisruptorAcknowledgment(event, ringBuffer, sequence);
                consume(listener, mqEvent, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                logger().error("Consume MQ [{}] failed", listener.namespaceTopicTags(), ex);
            }
        }
    }

    /**
     * 路由键：{@code namespace.topic[.tag]}（用 Event 实例拼接，避免类外暴露）。
     */
    private static String resolveRouteKey(MQListener listener) {
        Event probe = new Event();
        probe.namespace = listener.getNamespace();
        probe.topic = listener.getTopic();
        probe.tag = TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null);
        return probe.routeKey();
    }

    @Override
    public void start() {
        // RingBuffer 在构造时已 start，留空对齐 MQClient.start() 契约。
    }

    /**
     * 关闭底层 Disruptor（应用关闭或测试清理时调用）。
     */
    public void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
            logger().info("DisruptorMQClient shutdown");
        }
    }

    /**
     * 启动 RingBuffer（双构造 1 调用）。
     */
    private void startRingBuffer() {
        int bufferSize = normalizeBufferSize(properties.getBufferSize());
        WaitStrategy waitStrategy = resolveWaitStrategy(properties.getWaitStrategy());
        Disruptor<Event> d = new Disruptor<>(Event::new, bufferSize,
                DaemonThreadFactory.INSTANCE, ProducerType.MULTI, waitStrategy);
        d.handleEventsWith(this::onEvent);
        ringBuffer = d.start();
        this.disruptor = d;
        logger().info("Disruptor RingBuffer started: bufferSize={}, waitStrategy={}",
                bufferSize, waitStrategy.getClass().getSimpleName());
    }

    private static int normalizeBufferSize(int requested) {
        int size = 1;
        while (size < requested && size < (1 << 20)) {
            size <<= 1;
        }
        return size;
    }

    private static WaitStrategy resolveWaitStrategy(String name) {
        if (!StrKit.hasText(name)) {
            return new YieldingWaitStrategy();
        }
        return switch (name.trim().toLowerCase()) {
            case "blocking" -> new BlockingWaitStrategy();
            case "busyspin", "busy-spin" -> new BusySpinWaitStrategy();
            default -> new YieldingWaitStrategy();
        };
    }

    /**
     * RingBuffer 事件槽（{@link DisruptorMQClient} 嵌套类）。
     *
     * <p>字段 public 是 Disruptor RingBuffer 高性能访问惯例，由 producer 在 claim 后填充、
     * consumer 在 onEvent 中读取。不暴露 setter 以避免误用。
     */
    public static class Event implements com.lmax.disruptor.EventFactory<Event> {

        public String topic;
        public String tag;
        public String namespace;
        public String messageId;
        public String payload;
        public long sequence;

        @Override
        public Event newInstance() {
            return new Event();
        }

        /**
         * 物理路由键：{@code namespace.topic[.tag]}。
         */
        public String routeKey() {
            String base = (namespace == null ? "" : namespace) + "." + (topic == null ? "" : topic);
            if (tag == null || StrKit.isBlank(tag) || "*".equals(tag)) {
                return base;
            }
            return base + "." + tag;
        }
    }
}
