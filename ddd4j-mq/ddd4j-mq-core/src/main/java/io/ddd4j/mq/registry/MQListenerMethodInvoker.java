package io.ddd4j.mq.registry;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.domain.event.MQEvent;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.mq.ack.AckDisposition;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.consume.MQConsumerContext;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.contract.MQMessages;
import io.ddd4j.mq.serialization.MQEventSerialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Objects;

/**
 * 反射调用 {@link MQListenerDefinition} 目标方法，解析参数与 {@link AckDisposition} 返回值。
 *
 * <p>已彻底移除对 {@code org.springframework.messaging.Message} 的依赖，
 * 全部基于纯 Java {@link MQMessage} 模型。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerMethodInvoker {

    private final MQEventSerialization serialization;

    private static boolean isInfrastructureParameter(Class<?> type) {
        return MQConsumerContext.class.isAssignableFrom(type)
                || MessageAcknowledgment.class.isAssignableFrom(type)
                || MQMessage.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type);
    }

    private static boolean hasText(String s) {
        return Objects.nonNull(s) && !io.ddd4j.kit.lang.StrKit.isBlank(s);
    }

    /**
     * 调用监听器方法并返回业务处置结果。
     */
    public AckDisposition invoke(MQListenerDefinition definition, MQConsumerContext context,
                                 MQMessage<?> message) throws Exception {
        return invoke(definition, context, message, resolvePayload(definition, message));
    }

    /**
     * 调用监听器方法并返回业务处置结果。
     */
    public AckDisposition invoke(
            MQListenerDefinition definition,
            MQConsumerContext context,
            MQMessage<?> message,
            Object payload) throws Exception {

        MQListenerScanner.prepareMethod(definition);
        Method method = definition.getMethod();
        if (payload instanceof MQEvent && !((MQEvent) payload).supports(definition.supports())) {
            return AckDisposition.DISCARD;
        }

        Object[] args = resolveArguments(method, context, message, payload);
        Object result = method.invoke(definition.getBean(), args);
        return resolveDisposition(result);
    }

    /**
     * 反序列化消息载荷为监听器方法所需类型。
     */
    public Object resolvePayload(MQListenerDefinition definition, MQMessage<?> message) {
        Class<?> payloadType = resolvePayloadType(definition.getMethod());
        if (Objects.isNull(payloadType) || payloadType == Void.class) {
            return message.getPayload();
        }
        Object raw = message.getPayload();
        if (Objects.isNull(raw)) {
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
     * 解析监听器方法的载荷参数类型（跳过上下文/ack/MQMessage/Map 参数）。
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
            } else if (Map.class.isAssignableFrom(type)) {
                args[i] = message.getHeaders();
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
        if (Objects.isNull(result)) {
            return AckDisposition.ACK;
        }
        if (result instanceof AckDisposition) {
            return (AckDisposition) result;
        }
        if (result instanceof Boolean) {
            return (Boolean) result ? AckDisposition.ACK : AckDisposition.REQUEUE;
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
        return buildContext(definition, message, acknowledgment, null);
    }

    /**
     * 构建消费上下文并注入租户 ThreadContext。
     */
    public MQConsumerContext buildContext(
            MQListenerDefinition definition,
            MQMessage<?> message,
            MessageAcknowledgment acknowledgment,
            Object payload) {

        String tenantId = resolveTenantId(message);
        if (hasText(tenantId)) {
            ThreadContext.set(ContextConstants.TENANT_ID, tenantId);
        }

        MQDestination destination = MQDestination.of(
                definition.getTopic(),
                definition.getTags(),
                definition.getNamespace());

        return MQConsumerContext.builder()
                .tenantId(tenantId)
                .acknowledgment(acknowledgment)
                .message(message)
                .payload(payload)
                .destination(destination)
                .build();
    }

    /**
     * 从消息头或载荷中提取租户 ID。
     */
    private String resolveTenantId(MQMessage<?> message) {
        String headerTenant = MQMessages.headerAsString(message, "tenantId");
        if (hasText(headerTenant)) {
            return headerTenant;
        }
        String stdTenant = MQMessages.extractTenantId(message);
        if (hasText(stdTenant)) {
            return stdTenant;
        }
        Object payload = message.getPayload();
        if (payload instanceof MQEvent && hasText(((MQEvent) payload).getTenantId())) {
            return ((MQEvent) payload).getTenantId();
        }
        return ThreadContext.get(ContextConstants.TENANT_ID);
    }

    /**
     * 清理租户 ThreadContext。
     */
    public void clearContext() {
        ThreadContext.clear();
    }
}
