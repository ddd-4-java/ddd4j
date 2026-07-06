package io.ddd4j.mq.disruptor.consumer;

import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.disruptor.core.DisruptorMQBus;
import io.ddd4j.mq.listener.ListenerDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@code @EventListener} 注册到 Disruptor 事件分发器。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class DisruptorMQConsumerEndpointRegistrar {

    private final DisruptorMQBus disruptorMQBus;
    private final List<ListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();

    /**
     * 注册单个监听器定义。
     */
    public void register(ListenerDefinition definition, ConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");
        disruptorMQBus.dispatcher().register(definition, handler);
        registeredDefinitions.add(definition);
    }

    /**
     * 批量注册监听器。
     */
    public void registerAll(List<ListenerDefinition> definitions, ConsumerHandler handler) {
        if (Objects.isNull(definitions) || definitions.isEmpty()) {
            return;
        }
        for (ListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("Disruptor consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    /**
     * 返回已登记的监听器定义。
     */
    public List<ListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }
}
