package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.consume.ConsumerEngine;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.listener.ListenerMethodInvoker;
import io.ddd4j.mq.listener.ListenerScanner;
import io.ddd4j.mq.serialization.EventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;
import io.ddd4j.mq.spi.BrokerAdapters;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Objects;

/**
 * 应用就绪后扫描 {@link io.ddd4j.mq.annotation.EventListener} 并通过 {@link BrokerAdapter} 动态注册消费端点。
 *
 * <p>本类仅负责 Spring 容器的监听器扫描和适配器选择，
 * 消费模板逻辑（preCheck → invoke → disposition → afterConsume）由 {@link ConsumerEngine} 统一驱动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerRegistrar {

    private final ListenerScanner scanner;
    private final List<BrokerAdapter> adapters;
    private final MQProperties properties;
    private final EventSerialization serialization;
    private final List<ConsumerInterceptor> interceptors;

    /**
     * 应用就绪时扫描监听器并注册到当前 Broker Adapter。
     */
    @Order
    @EventListener
    public void onApplicationReady(ContextRefreshedEvent event) {
        if (Objects.nonNull(event.getApplicationContext().getParent())) {
            return;
        }
        List<ListenerDefinition> definitions = scanner.scan();
        if (definitions.isEmpty()) {
            log.debug("No @EventListener definitions to register");
            return;
        }

        BrokerAdapter adapter = BrokerAdapters.selectAdapter(adapters, properties);
        ListenerMethodInvoker invoker = new ListenerMethodInvoker(serialization);
        ConsumerEngine engine = new ConsumerEngine(invoker, interceptors, properties);

        for (ListenerDefinition definition : definitions) {
            ConsumerHandler handler = engine.createHandler(definition, adapter);
            adapter.registerConsumer(definition, handler);
            log.info("Registered MQ listener: bean={}, method={}, topic={}, group={}",
                    definition.getBean() != null
                            ? definition.getBean().getClass().getSimpleName()
                            : definition.getBeanName(),
                    definition.getMethod().getName(),
                    definition.getTopic(),
                    definition.getGroup());
        }
    }
}
