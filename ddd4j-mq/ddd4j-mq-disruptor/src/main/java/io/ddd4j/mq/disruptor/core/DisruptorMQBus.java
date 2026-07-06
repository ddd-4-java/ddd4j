package io.ddd4j.mq.disruptor.core;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.ddd4j.mq.disruptor.config.DisruptorMQProperties;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;

/**
 * LMAX Disruptor RingBuffer 生命周期管理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class DisruptorMQBus {

    private final DisruptorMQProperties properties;
    private final DisruptorMQEventDispatcher dispatcher;
    private Disruptor<DisruptorMQEvent> disruptor;

    @Getter
    private RingBuffer<DisruptorMQEvent> ringBuffer;

    /**
     * @param properties Disruptor 配置
     * @param dispatcher 事件分发器
     */
    public DisruptorMQBus(DisruptorMQProperties properties, DisruptorMQEventDispatcher dispatcher) {
        this.properties = properties;
        this.dispatcher = dispatcher;
        start();
    }

    /**
     * 发布 JSON 载荷到 RingBuffer。
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
     * 返回事件分发器（注册消费端用）。
     */
    public DisruptorMQEventDispatcher dispatcher() {
        return dispatcher;
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
        disruptor.handleEventsWith(dispatcher);
        ringBuffer = disruptor.start();
        dispatcher.bindRingBuffer(ringBuffer);
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
