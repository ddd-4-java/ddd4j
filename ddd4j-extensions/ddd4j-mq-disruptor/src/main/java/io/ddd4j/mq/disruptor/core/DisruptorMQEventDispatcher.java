package io.ddd4j.mq.disruptor.core;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import io.ddd4j.mq.disruptor.acknowledgment.DisruptorMessageAcknowledgment;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Disruptor 事件分发器：按 routeKey 将 RingBuffer 事件路由到已注册的 {@link MQConsumerHandler}。
 */
@Slf4j
public class DisruptorMQEventDispatcher implements EventHandler<DisruptorMQEvent> {

    private final Map<String, List<RegisteredHandler>> handlersByRoute = new ConcurrentHashMap<>();
    private RingBuffer<DisruptorMQEvent> ringBuffer;

    /**
     * 绑定 RingBuffer（Disruptor 启动后注入，用于 requeue）。
     */
    public void bindRingBuffer(RingBuffer<DisruptorMQEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    /**
     * 注册消费处理器。
     *
     * @param definition 监听器定义
     * @param handler    消费处理函数
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
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
        if (event == null || event.getTopic() == null) {
            return;
        }
        String routeKey = event.routeKey();
        List<RegisteredHandler> handlers = handlersByRoute.get(routeKey);
        if (handlers == null || handlers.isEmpty()) {
            log.trace("No Disruptor handler for routeKey={}", routeKey);
            event.clear();
            return;
        }
        for (RegisteredHandler registered : handlers) {
            try {
                DisruptorMessageAcknowledgment ack = new DisruptorMessageAcknowledgment(
                        event, ringBuffer, sequence);
                Map<String, Object> headers = new HashMap<>();
                headers.put("topic", event.getTopic());
                headers.put("tag", event.getTag());
                headers.put("namespace", event.getNamespace());
                MQMessage<String> message = MQMessage.of(
                        event.getPayload(),
                        headers,
                        event.getMessageId(),
                        event.getCorrelationId(),
                        event);
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
    private String buildRouteKey(MQListenerDefinition definition) {
        String namespace = definition.getNamespace();
        String topic = definition.getTopic();
        String tags = definition.getTags();
        String tag = (!StringUtils.hasText(tags) || "*".equals(tags.trim()))
                ? null
                : (tags.contains("||") ? tags.substring(0, tags.indexOf("||")).trim() : tags.trim());
        DisruptorMQEvent probe = new DisruptorMQEvent();
        probe.setNamespace(namespace);
        probe.setTopic(topic);
        probe.setTag(tag);
        return probe.routeKey();
    }

    /**
     * 已注册 handler 记录。
     */
    private static final class RegisteredHandler {

        private final MQListenerDefinition definition;
        private final MQConsumerHandler handler;

        private RegisteredHandler(MQListenerDefinition definition, MQConsumerHandler handler) {
            this.definition = definition;
            this.handler = handler;
        }

        private MQListenerDefinition definition() {
            return definition;
        }

        private MQConsumerHandler handler() {
            return handler;
        }
    }
}
