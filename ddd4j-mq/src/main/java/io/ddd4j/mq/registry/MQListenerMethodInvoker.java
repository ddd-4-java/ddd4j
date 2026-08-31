package io.ddd4j.mq.registry;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.core.contract.constant.ContextConstants;
import io.ddd4j.mq.acknowledgment.AckDisposition;
import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.consume.MQConsumerContext;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.serialization.MQEventSerialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;

/**
 * 反射调用 {@link MQListenerDefinition} 目标方法，解析参数与 {@link AckDisposition} 返回值。
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerMethodInvoker {

    private final MQEventSerialization serialization;

    /**
     * 调用监听器方法并返回业务处置结果。
     *
     * @param definition 监听器定义
     * @param context    消费上下文
     * @param message    消息信封
     * @return 业务处置结果；void 返回时默认 {@link AckDisposition#ACK}
     */
    public AckDisposition invoke(MQListenerDefinition definition, MQConsumerContext context, MQMessage<?> message)
            throws Exception {

        MQListenerScanner.prepareMethod(definition);
        Method method = definition.getMethod();
        Object payload = resolvePayload(definition, message);
        if (payload instanceof MQEvent) {
            MQEvent mqEvent = (MQEvent) payload;
            if (!mqEvent.supports(definition.supports())) {
                return AckDisposition.DISCARD;
            }
        }

        Object[] args = resolveArguments(method, context, message, payload);
        Object result = method.invoke(definition.getBean(), args);
        return resolveDisposition(result);
    }

    /**
     * 反序列化消息载荷为监听器方法所需类型。
     */
    private Object resolvePayload(MQListenerDefinition definition, MQMessage<?> message) {
        Class<?> payloadType = resolvePayloadType(definition.getMethod());
        if (payloadType == null || payloadType == Void.class) {
            return message.payload();
        }
        Object raw = message.payload();
        if (raw == null) {
            return null;
        }
        if (payloadType.isInstance(raw)) {
            return raw;
        }
        if (raw instanceof String) {
            return serialization.deserialize((String) raw, payloadType);
        }
        return serialization.deserialize(serialization.serialize(raw), payloadType);
    }

    /**
     * 解析监听器方法的载荷参数类型（跳过上下文/ack/MQMessage 参数）。
     */
    private Class<?> resolvePayloadType(Method method) {
        for (Parameter parameter : method.getParameters()) {
            Class<?> type = parameter.getType();
            if (isInfrastructureParameter(type)) {
                continue;
            }
            return type;
        }
        return null;
    }

    /**
     * 按方法签名组装调用参数。
     */
    private Object[] resolveArguments(
            Method method,
            MQConsumerContext context,
            MQMessage<?> message,
            Object payload) {

        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();
            if (MQConsumerContext.class.isAssignableFrom(type)) {
                args[i] = context;
            } else if (MessageAcknowledgment.class.isAssignableFrom(type)) {
                args[i] = context.getAcknowledgment();
            } else if (MQMessage.class.isAssignableFrom(type)) {
                args[i] = message;
            } else {
                args[i] = payload;
            }
        }
        return args;
    }

    /**
     * 将方法返回值映射为 {@link AckDisposition}。
     */
    private AckDisposition resolveDisposition(Object result) {
        if (result == null) {
            return AckDisposition.ACK;
        }
        if (result instanceof AckDisposition) {
            return (AckDisposition) result;
        }
        if (result instanceof Boolean) {
            return ((Boolean) result) ? AckDisposition.ACK : AckDisposition.REQUEUE;
        }
        return AckDisposition.ACK;
    }

    /**
     * 构建消费上下文并注入租户 ThreadContext。
     */
    public MQConsumerContext buildContext(
            MQListenerDefinition definition,
            MQMessage<?> message,
            MessageAcknowledgment acknowledgment) {

        String tenantId = resolveTenantId(message);
        if (StringUtils.hasText(tenantId)) {
            ThreadContext.set(ContextConstants.TENANT_ID, tenantId);
        }

        MQDestination destination = MQDestination.of(
                definition.getTopic(),
                definition.getTags(),
                definition.getNamespace());

        return MQConsumerContext.builder()
                .tenantId(tenantId)
                .acknowledgment(acknowledgment)
                .headers(message.headers())
                .message(message)
                .destination(destination)
                .build();
    }

    /**
     * 从消息头或载荷中提取租户 ID。
     */
    private String resolveTenantId(MQMessage<?> message) {
        String headerTenant = message.headerAsString("tenantId");
        if (StringUtils.hasText(headerTenant)) {
            return headerTenant;
        }
        Object payload = message.payload();
        if (payload instanceof MQEvent) {
            MQEvent mqEvent = (MQEvent) payload;
            if (StringUtils.hasText(mqEvent.getTenantId())) {
                return mqEvent.getTenantId();
            }
        }
        return ThreadContext.get(ContextConstants.TENANT_ID);
    }

    /**
     * 清理租户 ThreadContext。
     */
    public void clearContext() {
        ThreadContext.clear();
    }

    private static boolean isInfrastructureParameter(Class<?> type) {
        return MQConsumerContext.class.isAssignableFrom(type)
                || MessageAcknowledgment.class.isAssignableFrom(type)
                || MQMessage.class.isAssignableFrom(type);
    }
}
