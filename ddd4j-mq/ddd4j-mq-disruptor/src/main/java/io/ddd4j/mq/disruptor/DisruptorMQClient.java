package io.ddd4j.mq.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * LMAX Disruptor（进程内 MQ）客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>使用 ddd4j 自己的 {@link DisruptorEvent} 作为 RingBuffer 事件类型，
 * 以确保本地消息能力可由公开仓库独立构建。
 *
 * <h3>路由模型</h3>
 * <pre>
 *   namespace.topic.tag
 *   └───┬───┘ └─┬─┘ └┬┘
 *    环境隔离  业务分类  细分标签
 * </pre>
 *
 * <p>Disruptor 是进程内 RingBuffer，不存在可跨进程传输的 protocol header；稳定消息 ID 由
 * {@link MQEvent#getMsgId()} 同时保留在 payload 与 {@link DisruptorEvent} 字段中，不伪造
 * {@code ddd4j-message-id} metadata。
 *
 * <p>主线只有 {@link #initProducer(MQProperties)} 与 {@link #initConsumer(MQListener, MQProperties)}，
 * 核心业务逻辑全部内联。消费者模型：单 {@link com.lmax.disruptor.EventHandler} 内部遍历所有已注册 listener
 * （按 tag 过滤分发），对齐 base-mq RocketClient 的「集中消费者 + 应用层 tag 过滤」风格。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : DisruptorMQClient ###")
public class DisruptorMQClient implements MQClient {

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final DisruptorMQProperties properties;

    /**
     * 已注册监听器列表（{@link #initConsumer} 时累加，{@link #onEvent} 在 RingBuffer 回调里遍历）。
     */
    private final List<MQListener> listeners = new CopyOnWriteArrayList<>();

    /** \u6267\u884c\u4ee3\u7406\u5185\u90e8\u4e8b\u4ef6\u5206\u53d1\u7684 Disruptor\u3002 */
    private Disruptor<DisruptorEvent> disruptor;

    /** \u5411 Disruptor \u5de5\u4f5c\u7ebf\u7a0b\u8f6c\u4ea4\u6d88\u606f\u7684\u73af\u5f62\u7f13\u51b2\u533a\u3002 */
    @Getter
    private RingBuffer<DisruptorEvent> ringBuffer;

    /**
     * \u53cc\u6784\u9020 1\uff1a\u4f20\u5165\u914d\u7f6e\uff0c\u81ea\u5efa RingBuffer \u4e0e Disruptor\uff08\u6784\u9020\u5373\u542f\u52a8\uff0cconsumer \u7acb\u5373\u53ef\u7528\uff09\u3002
     *
     * @param properties Disruptor \u6d88\u606f\u914d\u7f6e
     */
    public DisruptorMQClient(DisruptorMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "DisruptorMQ properties is required");
        this.startRingBuffer();
    }

    /**
     * \u53cc\u6784\u9020 2\uff1a\u4f20\u5165\u5df2\u521d\u59cb\u5316\u7684 RingBuffer\uff08runtime \u96c6\u6210\u590d\u7528\u540c\u4e00 RingBuffer\uff0c\u4e8b\u4ef6\u7c7b\u578b\u5fc5\u987b\u4e3a DisruptorEvent\uff09\u3002
     *  \u4e0d\u542f\u52a8 Disruptor \u2014\u2014 RingBuffer \u5df2\u5c31\u7eea\uff0c\u8c03\u7528\u65b9\u8d1f\u8d23\u7ba1\u7406\u5176\u751f\u547d\u5468\u671f\u3002
     *
     * @param ringBuffer \u5df2\u521d\u59cb\u5316\u7684\u4e8b\u4ef6\u7f13\u51b2\u533a
     */
    public DisruptorMQClient(RingBuffer<DisruptorEvent> ringBuffer) {
        this.properties = new DisruptorMQProperties();
        this.ringBuffer = Objects.requireNonNull(ringBuffer, "DisruptorMQ ringBuffer is required");
    }

    /**
     * \u5411\u4e0a\u5bfb\u627e\u4e8c\u7684\u5e42\u6b21\u7f13\u51b2\u533a\u5927\u5c0f\uff0c\u9650\u5236\u5728\u5b9e\u73b0\u652f\u6301\u7684\u4e0a\u9650\u3002
     * @param requested \u914d\u7f6e\u4e2d\u8bf7\u6c42\u7684\u7f13\u51b2\u533a\u5927\u5c0f
     * @return \u7528\u4e8e\u521b\u5efa Disruptor \u7684\u7f13\u51b2\u533a\u5927\u5c0f
     */
    private static int normalizeBufferSize(int requested) {
        int size = 1;
        while (size < requested && size < (1 << 20)) {
            size <<= 1;
        }
        return size;
    }

    // ========================= 生产者 =========================

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"disruptor"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "disruptor";
    }

    // ========================= 消费者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        return event -> {
            String topic = resolveTopic(event, mqProperties);
            String payload = serialization().serialize(event);
            long sequence = ringBuffer.next();
            try {
                DisruptorEvent e = ringBuffer.get(sequence);
                e.setTopic(topic);
                e.setTag(event.getTag());             // private 字段走 setter
                e.setNamespace(event.getNamespace());
                e.setMessageId(event.getMsgId());
                e.setPayload(payload);                // private 字段走 setter
                e.setSequence(sequence);
            } finally {
                ringBuffer.publish(sequence);
            }
            log.info("Publish MQ [{}]: {}", topic, payload);
        };
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>listeners.add(listener)</code>。</p>
     *
     * @param listener 监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        log.info("Registered Disruptor consumer: bean={}, method={}",
                listener.getMethod().getDeclaringClass().getSimpleName(),
                listener.getMethod().getName());
        return true;
    }

    /**
     * RingBuffer \u4e8b\u4ef6\u56de\u8c03\u5165\u53e3\uff08{@link #start()} \u6ce8\u518c\u5230 Disruptor\uff09\u3002
     *
     *  <p>\u5339\u914d\u6d41\u7a0b\uff08\u7edf\u4e00\u9075\u5faa {@code namespace.topic[.tag]} \u8def\u7531\u6a21\u578b\uff09\uff1a
     *  <ol>
     *    <li>route key \u7cbe\u786e\u5339\u914d\uff1aevent \u7684 routeExpression vs listener \u7684 routeExpression</li>
     *    <li>{@link TagMatcher} \u4e8c\u6b21\u8fc7\u6ee4\uff08\u5e94\u5bf9 listener.tags \u8868\u8fbe\u5f0f\u5982 {@code "paid || shipped"}\uff09</li>
     *    <li>\u53cd\u5e8f\u5217\u5316 + \u53cd\u5c04\u8c03\u7528\uff08{@link MQClient#consume} \u9ed8\u8ba4\u65b9\u6cd5\uff09</li>
     *  </ol>
     *  \u4e0d\u5339\u914d\u7684 listener \u8df3\u8fc7\uff0c\u907f\u514d\u8de8 namespace/topic/tag \u7684\u8bef\u6d3e\u53d1\u3002
     *
     * @param event \u73af\u5f62\u961f\u5217\u4e2d\u5206\u914d\u7ed9\u6d88\u8d39\u8005\u7684\u4e8b\u4ef6
     * @param sequence Disruptor \u73af\u5f62\u961f\u5217\u4e2d\u7684\u4e8b\u4ef6\u5e8f\u53f7
     * @param endOfBatch \u662f\u5426\u4e3a\u5f53\u524d\u6279\u6b21\u7684\u6700\u540e\u4e00\u4e2a\u4e8b\u4ef6
     */
    private void onEvent(DisruptorEvent event, long sequence, boolean endOfBatch) {
        String eventRouteKey = event.getRouteExpression();   // namespace.topic[.tag]
        String eventTag = event.getTag();
        for (MQListener listener : listeners) {
            try {
                // 1. route key 精确匹配（统一规则：event.getRouteExpression() == resolveRouteKey(listener)）
                if (!eventRouteKey.equals(resolveRouteKey(listener))) {
                    continue;
                }
                // 2. TagMatcher 二次过滤（应对 listener.tags 表达式）
                if (!TagMatcher.match(eventTag, listener.getTags())) {
                    continue;
                }
                // 3. 反序列化 + 反射调用
                Object payload = event.getPayload();
                MQEvent mqEvent = serialization().deserialize(payload, listener.payloadType());
                if (Objects.isNull(mqEvent)) {
                    log.warn("Disruptor consume [{}] failed: mqEvent is null", eventRouteKey);
                    continue;
                }
                DisruptorAcknowledgment ack = new DisruptorAcknowledgment(event, ringBuffer, sequence);
                consume(listener, mqEvent, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                log.error("Consume MQ [{}] failed", eventRouteKey, ex);
            }
        }
    }

    /** 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。 */
    @Override
    public void start() {
        // Disruptor 在双构造 1 立即启动（双构造 2 RingBuffer 已就绪），
        // 此处留空对齐 MQClient.start() 契约。
    }

    /**
     * 启动 RingBuffer 与 Disruptor（双构造 1 自动调用，双构造 2 不调用）。
     */
    private void startRingBuffer() {
        int bufferSize = normalizeBufferSize(properties.getBufferSize());
        WaitStrategy waitStrategy = properties.getWaitStrategy().instance();
        Disruptor<DisruptorEvent> d = new Disruptor<>(DisruptorEvent::new, bufferSize,
                DaemonThreadFactory.INSTANCE, ProducerType.MULTI, waitStrategy);
        d.handleEventsWith(this::onEvent);
        ringBuffer = d.start();
        this.disruptor = d;
        log.info("Disruptor RingBuffer started: bufferSize={}, waitStrategy={}",
                bufferSize, waitStrategy.getClass().getSimpleName());
    }

    /**
     * 关闭底层 Disruptor（应用关闭或测试清理时调用）。
     */
    @Override
    public void close() {
        if (Objects.nonNull(disruptor)) {
            disruptor.shutdown();
            log.info("DisruptorMQClient shutdown");
        }
    }
}
