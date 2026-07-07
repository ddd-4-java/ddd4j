package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.consume.ConsumerEngine;
import io.ddd4j.mq.consume.MQEventConsumer;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.event.MQEventStorer;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.listener.MQListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Objects;

/**
 * 应用就绪后扫描 {@link MQListener} 并通过 {@link MQEventConsumer} 订阅。
 *
 * <p>本类仅负责 Spring 容器的监听器扫描和消费者订阅，
 * 消费横切逻辑（反序列化 → 策略匹配 → 租户注入 → 持久化 → 反射调用）由 {@link ConsumerEngine} 统一驱动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerRegistrar {

    private final MQListenerBeanPostProcessor beanPostProcessor;
    private final List<MQEventConsumer> consumers;
    private final MQProperties properties;
    private final MQEventSerialization serialization;
    private final ObjectProvider<MQEventStorer<?>> storerProvider;

    /**
     * 应用就绪时扫描监听器并订阅到当前 MQ 消费者。
     */
    @Order
    @EventListener
    public void onApplicationReady(ContextRefreshedEvent event) {
        if (Objects.nonNull(event.getApplicationContext().getParent())) {
            return;
        }
        List<MQListener> listeners = beanPostProcessor.getListeners();
        if (listeners.isEmpty()) {
            log.debug("No @MQEventListener definitions to subscribe");
            return;
        }
        if (Objects.isNull(consumers) || consumers.isEmpty()) {
            log.warn("No MQEventConsumer bean found, {} listener(s) will not be subscribed", listeners.size());
            return;
        }

        @SuppressWarnings("rawtypes")
        MQEventStorer storer = storerProvider.getIfAvailable();
        ConsumerEngine engine = new ConsumerEngine(serialization, properties, storer);

        for (MQListener listener : listeners) {
            MQEventConsumer.MQEventCallback callback = engine.callback(listener);
            for (MQEventConsumer consumer : consumers) {
                consumer.subscribe(listener, callback);
                log.info("Subscribed MQ listener: bean={}, method={}, topic={}, group={}",
                        listener.getBean() != null
                                ? listener.getBean().getClass().getSimpleName()
                                : "unknown",
                        listener.getMethod().getName(),
                        listener.getTopic(),
                        listener.getGroup());
            }
        }
    }
}
