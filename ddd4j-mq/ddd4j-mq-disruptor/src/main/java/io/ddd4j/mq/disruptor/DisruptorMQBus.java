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
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * LMAX Disruptor RingBuffer 生命周期管理 + 进程内发布入口。
 *
 * <p>由 {@link DisruptorMQClient} 持有：{@link #publish} 用于生产者把消息推进 RingBuffer，
 * 注册的 {@link Consumer} 用于消费者端在 RingBuffer 事件到达时执行分发逻辑。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class DisruptorMQBus {

    private final DisruptorMQProperties properties;
    private final Consumer<DisruptorMQEvent> onEvent;
    private Disruptor<DisruptorMQEvent> disruptor;

    @Getter
    private RingBuffer<DisruptorMQEvent> ringBuffer;

    /**
     * @param properties Disruptor 配置
     * @param onEvent    RingBuffer 事件回调（由 {@link DisruptorMQClient} 提供，转发给已注册监听器）
     */
    public DisruptorMQBus(DisruptorMQProperties properties, Consumer<DisruptorMQEvent> onEvent) {
        this.properties = properties;
        this.onEvent = onEvent;
        start();
    }

    /**
     * 发布一个事件到 RingBuffer（由 {@link DisruptorMQClient#initProducer} 返回的 lambda 调用）。
     */
    public void publish(String namespace, String topic, String tag, String messageId, String payload) {
        long sequence = ringBuffer.next();
        try {
            DisruptorMQEvent event = ringBuffer.get(sequence);
            event.copyFrom(namespace, topic, tag, messageId, null, payload, sequence);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * 启动 Disruptor。
     */
    private void start() {
        int bufferSize = normalizeBufferSize(properties.getBufferSize());
        WaitStrategy waitStrategy = resolveWaitStrategy(properties.getWaitStrategy());
        disruptor = new Disruptor<>(
                DisruptorMQEvent::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                waitStrategy);
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            if (Objects.nonNull(onEvent)) {
                onEvent.accept(event);
            }
        });
        ringBuffer = disruptor.start();
        log.info("DisruptorMQBus started: bufferSize={}, waitStrategy={}", bufferSize, waitStrategy.getClass().getSimpleName());
    }

    /**
     * 关闭 Disruptor。
     */
    @PreDestroy
    public void shutdown() {
        if (Objects.nonNull(disruptor)) {
            disruptor.shutdown();
            log.info("DisruptorMQBus shutdown");
        }
    }

    /**
     * 保证 bufferSize 为 2 的幂。
     */
    private int normalizeBufferSize(int requested) {
        int size = 1;
        while (size < requested && size < (1 << 20)) {
            size <<= 1;
        }
        return size;
    }

    /**
     * 解析等待策略配置。
     */
    private WaitStrategy resolveWaitStrategy(String name) {
        if (!StrKit.hasText(name)) {
            return new YieldingWaitStrategy();
        }
        return switch (name.trim().toLowerCase()) {
            case "blocking" -> new BlockingWaitStrategy();
            case "busyspin", "busy-spin" -> new BusySpinWaitStrategy();
            default -> new YieldingWaitStrategy();
        };
    }
}
