package io.ddd4j.mq.disruptor.core;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import io.ddd4j.mq.consume.MQEventConsumer;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.disruptor.ack.DisruptorAcknowledgment;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Disruptor 事件分发器：按 routeKey 将 RingBuffer 事件路由到已注册的 {@link MQEventCallback}。
 *
 * <p>同时实现 {@link EventHandler}（Disruptor 内部事件处理器）与 {@link MQEventConsumer}（ddd4j MQ 消费契约）。
 * {@link #subscribe(MQListener, MQEventCallback)} 完成监听器注册，
 * {@link #onEvent(DisruptorMQEvent, long, boolean)} 由 Disruptor 调用并回调 {@link MQEventCallback}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class DisruptorMQEventDispatcher implements EventHandler<DisruptorMQEvent>, MQEventConsumer {

    private final Map<String, List<RegisteredListener>> handlersByRoute = new ConcurrentHashMap<>();
    private RingBuffer<DisruptorMQEvent> ringBuffer;

    /**
     * 绑定 RingBuffer（Disruptor 启动后注入，用于 requeue）。
     *
     * @param ringBuffer Disruptor RingBuffer
     */
    public void bindRingBuffer(RingBuffer<DisruptorMQEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void subscribe(MQListener listener, MQEventCallback onEvent) {
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(onEvent, "onEvent");
        String routeKey = buildRouteKey(listener);
        handlersByRoute
                .computeIfAbsent(routeKey, key -> new CopyOnWriteArrayList<>())
                .add(new RegisteredListener(listener, onEvent));
        log.info("Registered Disruptor consumer: routeKey={}, bean={}, method={}",
                routeKey,
                listener.getMethod().getDeclaringClass().getSimpleName(),
                listener.getMethod().getName());
    }

    /**
     * 消费 RingBuffer 事件并委托已注册的回调。
     */
    @Override
    public void onEvent(DisruptorMQEvent event, long sequence, boolean endOfBatch) {
        if (Objects.isNull(event) || Objects.isNull(event.getTopic())) {
            return;
        }
        String routeKey = event.routeKey();
        List<RegisteredListener> handlers = handlersByRoute.get(routeKey);
        if (Objects.isNull(handlers) || handlers.isEmpty()) {
            log.trace("No Disruptor handler for routeKey={}", routeKey);
            event.clear();
            return;
        }
        for (RegisteredListener registered : handlers) {
            try {
                Acknowledgment ack = new DisruptorAcknowledgment(event, ringBuffer, sequence);
                registered.onEvent().onEvent(
                        event.getPayload(),
                        event.getMessageId(),
                        null,
                        event.getTag(),
                        ack);
            } catch (Throwable ex) {
                log.error("Disruptor consumer failed: routeKey={}", routeKey, ex);
            }
        }
        event.clear();
    }

    /**
     * 根据监听器定义构建 routeKey。
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
    private record RegisteredListener(MQListener listener, MQEventCallback onEvent) {
    }
}
