package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.consume.MQConsumeEngine;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerMethodInvoker;
import io.ddd4j.mq.registry.MQListenerScanner;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import io.ddd4j.mq.spi.MQBrokerAdapters;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Objects;

/**
 * 应用就绪后扫描 {@link io.ddd4j.mq.annotation.MQEventListener} 并通过 {@link MQBrokerAdapter} 动态注册消费端点。
 *
 * <p>本类仅负责 Spring 容器的监听器扫描和适配器选择，
 * 消费模板逻辑（preCheck → invoke → disposition → afterConsume）由 {@link MQConsumeEngine} 统一驱动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerRegistrar {

    private final MQListenerScanner scanner;
    private final List<MQBrokerAdapter> adapters;
    private final Ddd4jMQProperties properties;
    private final MQEventSerialization serialization;
    private final List<MQConsumeInterceptor> interceptors;

    /**
     * 应用就绪时扫描监听器并注册到当前 Broker Adapter。
     */
    @Order
    @EventListener
    public void onApplicationReady(ContextRefreshedEvent event) {
        if (Objects.nonNull(event.getApplicationContext().getParent())) {
            return;
        }
        List<MQListenerDefinition> definitions = scanner.scan();
        if (definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions to register");
            return;
        }

        MQBrokerAdapter adapter = MQBrokerAdapters.selectAdapter(adapters, properties);
        MQListenerMethodInvoker invoker = new MQListenerMethodInvoker(serialization);
        MQConsumeEngine engine = new MQConsumeEngine(invoker, interceptors, properties);

        for (MQListenerDefinition definition : definitions) {
            MQConsumerHandler handler = engine.createHandler(definition, adapter);
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
