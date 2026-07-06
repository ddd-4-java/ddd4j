package io.ddd4j.mq.disruptor.core;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.MessageConverter;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.disruptor.ack.DisruptorAcknowledgment;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.listener.EndpointNaming;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Disruptor 事件分发器：按 routeKey 将 RingBuffer 事件路由到已注册的 {@link ConsumerHandler}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class DisruptorMQEventDispatcher implements EventHandler<DisruptorMQEvent> {

    private final Map<String, List<RegisteredHandler>> handlersByRoute = new ConcurrentHashMap<>();
    private RingBuffer<DisruptorMQEvent> ringBuffer;

    /**
     * Disruptor 原生消息 → Message 转换器。
     */
    private static final MessageConverter<DisruptorMQEvent> CONVERTER = event -> {
        Map<String, Object> headers = new java.util.HashMap<>();
        headers.put("topic", event.getTopic());
        headers.put("tag", event.getTag());
        headers.put("namespace", event.getNamespace());
        return Message.of(
                event.getPayload(),
                headers,
                event.getMessageId(),
                event.getCorrelationId(),
                event);
    };

    /**
     * 绑定 RingBuffer（Disruptor 启动后注入，用于 requeue）。
     */
    public void bindRingBuffer(RingBuffer<DisruptorMQEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    /**
     * 注册消费处理器。
     */
    public void register(ListenerDefinition definition, ConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");
        String routeKey = buildRouteKey(definition);
        handlersByRoute
                .computeIfAbsent(routeKey, key -> new CopyOnWriteArrayList<>())
                .add(new RegisteredHandler(definition, handler));
        log.info("Registered Disruptor consumer: routeKey={}, bean={}, method={}",
                routeKey,
                definition.getMethod().getDeclaringClass().getSimpleName(),
                definition.getMethod().getName());
    }

    /**
     * 消费 RingBuffer 事件并委托已注册 handler。
     */
    @Override
    public void onEvent(DisruptorMQEvent event, long sequence, boolean endOfBatch) {
        if (Objects.isNull(event) || Objects.isNull(event.getTopic())) {
            return;
        }
        String routeKey = event.routeKey();
        List<RegisteredHandler> handlers = handlersByRoute.get(routeKey);
        if (Objects.isNull(handlers) || handlers.isEmpty()) {
            log.trace("No Disruptor handler for routeKey={}", routeKey);
            event.clear();
            return;
        }
        Message<?> message = CONVERTER.convert(event);
        for (RegisteredHandler registered : handlers) {
            try {
                DisruptorAcknowledgment ack = new DisruptorAcknowledgment(
                        event, ringBuffer, sequence);
                registered.handler().handle(message, ack);
            } catch (Exception ex) {
                log.error("Disruptor consumer failed: routeKey={}", routeKey, ex);
            }
        }
        event.clear();
    }

    /**
     * 根据监听器定义构建 routeKey。
     */
    private String buildRouteKey(ListenerDefinition definition) {
        String namespace = definition.getNamespace();
        String topic = definition.getTopic();
        String tag = EndpointNaming.resolveTag(definition.getTags());
        DisruptorMQEvent probe = new DisruptorMQEvent();
        probe.setNamespace(namespace);
        probe.setTopic(topic);
        probe.setTag(tag);
        return probe.routeKey();
    }

    /**
     * 已注册 handler 记录。
     */
    private record RegisteredHandler(ListenerDefinition definition, ConsumerHandler handler) {
    }
}
