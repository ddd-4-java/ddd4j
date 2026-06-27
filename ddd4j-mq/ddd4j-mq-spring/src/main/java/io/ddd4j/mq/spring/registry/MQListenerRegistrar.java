package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.ack.AckDisposition;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.MQConsumeTemplates;
import io.ddd4j.mq.ack.NoOpMessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumeInterceptor;
import io.ddd4j.mq.consume.MQConsumerContext;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerMethodInvoker;
import io.ddd4j.mq.registry.MQListenerScanner;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import io.ddd4j.mq.spi.MQBrokerAdapters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 应用就绪后扫描 {@link io.ddd4j.core.contract.annotation.MQEventListener} 并通过 {@link MQBrokerAdapter} 动态注册消费端点（从 ddd4j-mq-core 迁出）。
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
     *
     * @param event 应用就绪事件
     */
    @Order
    @EventListener
    public void onApplicationReady(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        List<MQListenerDefinition> definitions = scanner.scan();
        if (definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions to register");
            return;
        }

        MQBrokerAdapter adapter = MQBrokerAdapters.selectAdapter(adapters, properties);
        MQListenerMethodInvoker invoker = new MQListenerMethodInvoker(serialization);
        List<MQConsumeInterceptor> orderedInterceptors = orderedInterceptors();

        for (MQListenerDefinition definition : definitions) {
            MQConsumerHandler handler = createHandler(definition, adapter, invoker, orderedInterceptors);
            adapter.registerConsumer(definition, handler);
            log.info("Registered MQ listener: bean={}, method={}, topic={}, group={}",
                    definition.getBean().getClass().getSimpleName(),
                    definition.getMethod().getName(),
                    definition.getTopic(),
                    definition.getGroup());
        }
    }

    /**
     * 为单个监听器定义创建默认消费处理器。
     */
    private MQConsumerHandler createHandler(
            MQListenerDefinition definition,
            MQBrokerAdapter adapter,
            MQListenerMethodInvoker invoker,
            List<MQConsumeInterceptor> orderedInterceptors) {

        return (message, ack) -> {
            MessageAcknowledgment effectiveAck = resolveAcknowledgment(adapter, message, ack);
            MQConsumerContext context = invoker.buildContext(definition, message, effectiveAck);
            AtomicReference<AckDisposition> dispositionRef = new AtomicReference<>();
            try {
                MQConsumeTemplates.execute(
                        message,
                        effectiveAck,
                        () -> runPreCheck(orderedInterceptors, context, message),
                        () -> {
                            try {
                                AckDisposition disposition = invoker.invoke(definition, context, message);
                                dispositionRef.set(disposition);
                                return disposition;
                            } catch (Exception ex) {
                                log.error("MQ listener invocation failed: bean={}, method={}",
                                        definition.getBean().getClass().getSimpleName(),
                                        definition.getMethod().getName(),
                                        ex);
                                throw new RuntimeException(ex);
                            }
                        });
            } catch (Exception ex) {
                if (properties.getConsumer().isManualAck() && !effectiveAck.isAcknowledged()) {
                    effectiveAck.requeue();
                }
            } finally {
                runAfterConsume(orderedInterceptors, context, message, dispositionRef.get());
                invoker.clearContext();
            }
        };
    }

    /**
     * 解析确认端口：优先使用 Adapter 从 nativeMessage 解析，回退传入 ack 或 NoOp。
     */
    private MessageAcknowledgment resolveAcknowledgment(
            MQBrokerAdapter adapter,
            Message<?> message,
            MessageAcknowledgment ack) {

        MessageAcknowledgment resolved = adapter.resolveAcknowledgment(message);
        if (resolved != null) {
            return resolved;
        }
        return ack != null ? ack : new NoOpMessageAcknowledgment();
    }

    /**
     * 执行拦截链 preCheck，返回首个非零结果。
     */
    private int runPreCheck(
            List<MQConsumeInterceptor> orderedInterceptors,
            MQConsumerContext context,
            Message<?> message) {

        for (MQConsumeInterceptor interceptor : orderedInterceptors) {
            int result = interceptor.preCheck(context, message);
            if (result != MQConsumeTemplates.PRE_CONTINUE) {
                return result;
            }
        }
        return MQConsumeTemplates.PRE_CONTINUE;
    }

    /**
     * 执行拦截链 afterConsume 回调。
     */
    private void runAfterConsume(
            List<MQConsumeInterceptor> orderedInterceptors,
            MQConsumerContext context,
            Message<?> message,
            AckDisposition disposition) {

        for (MQConsumeInterceptor interceptor : orderedInterceptors) {
            try {
                interceptor.afterConsume(context, message, disposition);
            } catch (Exception ex) {
                log.warn("MQConsumeInterceptor afterConsume failed: {}", interceptor.getClass().getSimpleName(), ex);
            }
        }
    }

    /**
     * 按 order 升序排列拦截器。
     */
    private List<MQConsumeInterceptor> orderedInterceptors() {
        if (interceptors == null || interceptors.isEmpty()) {
            return List.of();
        }
        return interceptors.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(MQConsumeInterceptor::order))
                .toList();
    }
}
