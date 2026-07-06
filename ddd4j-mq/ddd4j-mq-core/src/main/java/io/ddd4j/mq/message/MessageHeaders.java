package io.ddd4j.mq.message;

import java.util.*;

/**
 * 标准 Header Keys 与消息便捷读取工具（纯 Java，零 Spring 依赖）。
 *
 * <p>工厂方法请直接使用 {@link Message#of(Object)} 系列。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class MessageHeaders {

    // ── 标准 Header Keys ──

    public static final String HEADER_NATIVE_MESSAGE = "ddd4j.native.message";

    public static final String HEADER_MESSAGE_ID = "ddd4j.message.id";

    public static final String HEADER_CORRELATION_ID = "ddd4j.correlation.id";

    public static final String HEADER_CAUSATION_ID = "ddd4j.causation.id";

    public static final String HEADER_TENANT_ID = "ddd4j.tenant.id";

    public static final String HEADER_BROKER_TYPE = "ddd4j.broker.type";

    public static final String HEADER_DESTINATION_TOPIC = "ddd4j.destination.topic";

    public static final String HEADER_DESTINATION_TAG = "ddd4j.destination.tag";

    public static final String HEADER_DESTINATION_NAMESPACE = "ddd4j.destination.namespace";

    private MessageHeaders() {
    }

    // ── Header 便捷读取 ──

    public static String headerAsString(Message<?> message, String key) {
        if (Objects.isNull(message) || Objects.isNull(key)) {
            return null;
        }
        Object v = message.header(key);
        return Objects.isNull(v) ? null : String.valueOf(v);
    }

    public static Object header(Message<?> message, String key) {
        if (Objects.isNull(message) || Objects.isNull(key)) {
            return null;
        }
        return message.header(key);
    }

    public static Map<String, Object> headers(Message<?> message) {
        if (Objects.isNull(message)) {
            return Collections.emptyMap();
        }
        return message.getHeaders();
    }

    public static <N> N nativeMessage(Message<?> message, Class<N> type) {
        if (Objects.isNull(message) || Objects.isNull(type)) {
            return null;
        }
        return message.nativeMessage(type);
    }

    // ── Header 提取 ──

    public static String extractMessageId(Message<?> message) {
        if (Objects.isNull(message)) {
            return null;
        }
        String id = message.getMessageId();
        if (Objects.nonNull(id)) {
            return id;
        }
        return headerAsString(message, HEADER_MESSAGE_ID);
    }

    public static String extractCorrelationId(Message<?> message) {
        if (Objects.isNull(message)) {
            return null;
        }
        String id = message.getCorrelationId();
        if (Objects.nonNull(id)) {
            return id;
        }
        return headerAsString(message, HEADER_CORRELATION_ID);
    }

    public static String extractTenantId(Message<?> message) {
        return headerAsString(message, HEADER_TENANT_ID);
    }

    public static Map<String, Object> copyHeaders(Message<?> message) {
        Map<String, Object> source = headers(message);
        if (source.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(source);
    }
}
