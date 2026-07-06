package io.ddd4j.mq.listener;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.mq.consume.AckType;
import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.consume.ConsumerContext;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.serialization.EventSerialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.ddd4j.kit.lang.StrKit;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Objects;

/**
 * 反射调用 {@link ListenerDefinition} 目标方法，解析参数与 {@link AckType} 返回值。
 *
 * <p>已彻底移除对 {@code org.springframework.messaging.Message} 的依赖，
 * 全部基于纯 Java {@link Message} 模型。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class ListenerMethodInvoker {

    private final EventSerialization serialization;

    private static boolean isInfrastructureParameter(Class<?> type) {
        return ConsumerContext.class.isAssignableFrom(type)
                || Acknowledgment.class.isAssignableFrom(type)
                || Message.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type);
    }


    /**
     * 调用监听器方法并返回业务处置结果。
     */
    public AckType invoke(ListenerDefinition definition, ConsumerContext context,
                                 Message<?> message) throws Exception {
        return invoke(definition, context, message, resolvePayload(definition, message));
    }

    /**
     * 调用监听器方法并返回业务处置结果。
     */
    public AckType invoke(
            ListenerDefinition definition,
            ConsumerContext context,
            Message<?> message,
            Object payload) throws Exception {

        ListenerScanner.prepareMethod(definition);
        Method method = definition.getMethod();
        if (payload instanceof MQEvent && !((MQEvent) payload).supports(definition.supports())) {
            return AckType.DISCARD;
        }

        Object[] args = resolveArguments(method, context, message, payload);
        Object result = method.invoke(definition.getBean(), args);
        return resolveDisposition(result);
    }

    /**
     * 反序列化消息载荷为监听器方法所需类型。
     */
    public Object resolvePayload(ListenerDefinition definition, Message<?> message) {
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
     * 解析监听器方法的载荷参数类型（跳过上下文/ack/Message/Map 参数）。
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
            ConsumerContext context,
            Message<?> message,
            Object payload) {

        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();
            if (ConsumerContext.class.isAssignableFrom(type)) {
                args[i] = context;
            } else if (Acknowledgment.class.isAssignableFrom(type)) {
                args[i] = context.getAcknowledgment();
            } else if (Message.class.isAssignableFrom(type)) {
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
     * 将方法返回值映射为 {@link AckType}。
     */
    private AckType resolveDisposition(Object result) {
        if (Objects.isNull(result)) {
            return AckType.ACK;
        }
        if (result instanceof AckType) {
            return (AckType) result;
        }
        if (result instanceof Boolean) {
            return (Boolean) result ? AckType.ACK : AckType.REQUEUE;
        }
        return AckType.ACK;
    }

    /**
     * 构建消费上下文并注入租户 ThreadContext。
     */
    public ConsumerContext buildContext(
            ListenerDefinition definition,
            Message<?> message,
            Acknowledgment acknowledgment) {
        return buildContext(definition, message, acknowledgment, null);
    }

    /**
     * 构建消费上下文并注入租户 ThreadContext。
     */
    public ConsumerContext buildContext(
            ListenerDefinition definition,
            Message<?> message,
            Acknowledgment acknowledgment,
            Object payload) {

        String tenantId = resolveTenantId(message);
        if (StrKit.hasText(tenantId)) {
            ThreadContext.set(ContextConstants.TENANT_ID, tenantId);
        }

        Destination destination = Destination.of(
                definition.getTopic(),
                definition.getTags(),
                definition.getNamespace());

        return ConsumerContext.builder()
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
    private String resolveTenantId(Message<?> message) {
        String headerTenant = MessageHeaders.headerAsString(message, "tenantId");
        if (StrKit.hasText(headerTenant)) {
            return headerTenant;
        }
        String stdTenant = MessageHeaders.extractTenantId(message);
        if (StrKit.hasText(stdTenant)) {
            return stdTenant;
        }
        Object payload = message.getPayload();
        if (payload instanceof MQEvent && StrKit.hasText(((MQEvent) payload).getTenantId())) {
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
