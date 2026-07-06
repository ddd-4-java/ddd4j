package io.ddd4j.mq.consume;

import io.ddd4j.mq.ack.AckDisposition;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.MQConsumeTemplates;
import io.ddd4j.mq.ack.NoOpMessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerMethodInvoker;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 消费模板引擎（纯 Java，零 Spring 依赖）。
 *
 * <p>把消费侧的统一模板（preCheck → invoke → applyDisposition → afterConsume → clearContext → 异常兜底）
 * 从下游框架适配层（如 ddd4j-mq-spring 的 MQListenerRegistrar）下沉到 core，
 * 让 Spring / Quarkus / Javalin 三框架共用同一套消费引擎。
 *
 * <p>使用方式（下游适配层）：
 * <pre>
 * MQConsumeEngine engine = new MQConsumeEngine(invoker, interceptors, properties);
 * MQConsumerHandler handler = engine.createHandler(definition, adapter);
 * adapter.registerConsumer(definition, handler);
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MQConsumeEngine {

    private final MQListenerMethodInvoker invoker;
    private final List<MQConsumeInterceptor> interceptors;
    private final Ddd4jMQProperties properties;

    /**
     * 为单个监听器定义创建消费处理器。
     *
     * <p>封装完整的消费流程：
     * <ol>
     *   <li>解析 ACK 端口（优先 Adapter 解析，兜底传入的 ack 或 NoOp）</li>
     *   <li>反序列化 payload</li>
     *   <li>构建消费上下文（注入租户 ThreadContext）</li>
     *   <li>执行消费模板：preCheck 拦截链 → 业务反射 → disposition 映射</li>
     *   <li>异常兜底：manualAck 模式下未确认则 requeue</li>
     *   <li>完成后回调拦截链 + 清理上下文</li>
     * </ol>
     *
     * @param definition 监听器定义
     * @param adapter    Broker 适配器（用于 resolveAcknowledgment 兜底）
     * @return 消费处理器
     */
    public MQConsumerHandler createHandler(MQListenerDefinition definition, MQBrokerAdapter adapter) {
        List<MQConsumeInterceptor> ordered = orderedInterceptors();

        return (message, ack) -> {
            MessageAcknowledgment effectiveAck = resolveAck(adapter, message, ack);
            Object payload = invoker.resolvePayload(definition, message);
            MQConsumerContext context = invoker.buildContext(definition, message, effectiveAck, payload);
            AtomicReference<AckDisposition> dispositionRef = new AtomicReference<>();

            try {
                MQConsumeTemplates.execute(
                        message,
                        effectiveAck,
                        () -> runPreCheck(ordered, context, message),
                        () -> {
                            try {
                                AckDisposition disposition = invoker.invoke(definition, context, message, payload);
                                dispositionRef.set(disposition);
                                return disposition;
                            } catch (Exception ex) {
                                log.error("MQ listener invocation failed: bean={}, method={}",
                                        definition.getBean() != null
                                                ? definition.getBean().getClass().getSimpleName()
                                                : definition.getBeanName(),
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
                runAfterConsume(ordered, context, message, dispositionRef.get());
                invoker.clearContext();
            }
        };
    }

    /**
     * 解析确认端口：优先使用 Adapter 从 nativeMessage 解析，回退传入 ack 或 NoOp。
     */
    private MessageAcknowledgment resolveAck(
            MQBrokerAdapter adapter,
            MQMessage<?> message,
            MessageAcknowledgment ack) {
        if (Objects.nonNull(adapter)) {
            MessageAcknowledgment resolved = adapter.resolveAcknowledgment(message);
            if (Objects.nonNull(resolved)) {
                return resolved;
            }
        }
        return Objects.nonNull(ack) ? ack : NoOpMessageAcknowledgment.INSTANCE;
    }

    /**
     * 执行拦截链 preCheck，返回首个非零结果。
     */
    private int runPreCheck(
            List<MQConsumeInterceptor> ordered,
            MQConsumerContext context,
            MQMessage<?> message) {
        for (MQConsumeInterceptor interceptor : ordered) {
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
            List<MQConsumeInterceptor> ordered,
            MQConsumerContext context,
            MQMessage<?> message,
            AckDisposition disposition) {
        for (MQConsumeInterceptor interceptor : ordered) {
            try {
                interceptor.afterConsume(context, message, disposition);
            } catch (Exception ex) {
                log.warn("MQConsumeInterceptor afterConsume failed: {}",
                        interceptor.getClass().getSimpleName(), ex);
            }
        }
    }

    /**
     * 按 order 升序排列拦截器。
     */
    private List<MQConsumeInterceptor> orderedInterceptors() {
        if (Objects.isNull(interceptors) || interceptors.isEmpty()) {
            return List.of();
        }
        return interceptors.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(MQConsumeInterceptor::order))
                .toList();
    }
}
