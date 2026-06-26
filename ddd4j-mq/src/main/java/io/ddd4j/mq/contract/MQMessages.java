package io.ddd4j.mq.contract;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 消息工具类：提供 {@link Message} 的便捷构建与 header 读取方法。
 * <p>
 * 替代原 {@code MQMessage}，统一使用 {@code org.springframework.messaging.Message} 作为消息模型。
 *
 * @author ddd4j
 * @since 1.0
 */
public final class MQMessages {

    // ── 标准 Header Keys ──

    /** Broker 原生消息逃生口 */
    public static final String HEADER_NATIVE_MESSAGE = "ddd4j.native.message";

    /** 关联 ID */
    public static final String HEADER_CORRELATION_ID = "ddd4j.correlation.id";

    /** 租户 ID */
    public static final String HEADER_TENANT_ID = "ddd4j.tenant.id";

    /** Broker 类型 */
    public static final String HEADER_BROKER_TYPE = "ddd4j.broker.type";

    /** 目标 topic */
    public static final String HEADER_DESTINATION_TOPIC = "ddd4j.destination.topic";

    /** 目标 tag */
    public static final String HEADER_DESTINATION_TAG = "ddd4j.destination.tag";

    /** 目标 namespace */
    public static final String HEADER_DESTINATION_NAMESPACE = "ddd4j.destination.namespace";

    private MQMessages() {
    }

    // ── 原生消息逃生口 ──

    /**
     * 从 Message headers 中安全获取原生消息并转型。
     *
     * @param message  消息
     * @param type     目标类型
     * @param <N>      目标类型
     * @return 原生消息实例，类型不匹配或不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    public static <N> N nativeMessage(Message<?> message, Class<N> type) {
        if (message == null || type == null) {
            return null;
        }
        Object nativeMsg = message.getHeaders().get(HEADER_NATIVE_MESSAGE);
        if (nativeMsg != null && type.isInstance(nativeMsg)) {
            return (N) nativeMsg;
        }
        return null;
    }

    // ── 便捷读取 ──

    /**
     * 读取 header 值并转为 String。
     *
     * @param message 消息
     * @param key     header key
     * @return String 值，不存在时返回 null
     */
    public static String headerAsString(Message<?> message, String key) {
        if (Objects.isNull(message) || Objects.isNull(key)) {
            return null;
        }
        Object v = message.getHeaders().get(key);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 读取 header 值。
     *
     * @param message 消息
     * @param key     header key
     * @return 值，不存在时返回 null
     */
    public static Object header(Message<?> message, String key) {
        if (Objects.isNull(message) || Objects.isNull(key)) {
            return null;
        }
        return message.getHeaders().get(key);
    }

    public static MessageHeaders headers(Message<?> message) {
        if (Objects.isNull(message)) {
            return new MessageHeaders(null);
        }
        return message.getHeaders();
    }

    // ── 构建方法 ──

    /**
     * 构建 Message（无原生消息）。
     *
     * @param payload  载荷
     * @param headers  header map
     * @param <T>      载荷类型
     * @return Message 实例
     */
    public static <T> Message<T> of(T payload, Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return MessageBuilder.withPayload(payload).build();
        }
        return MessageBuilder.withPayload(payload).copyHeaders(headers).build();
    }

    /**
     * 构建 Message（含原生消息逃生口）。
     *
     * @param payload       载荷
     * @param headers       header map
     * @param nativeMessage 原生消息
     * @param <T>           载荷类型
     * @return Message 实例
     */
    public static <T> Message<T> of(T payload, Map<String, Object> headers, Object nativeMessage) {
        MessageBuilder<T> builder = MessageBuilder.withPayload(payload);
        if (headers != null && !headers.isEmpty()) {
            builder.copyHeaders(headers);
        }
        if (nativeMessage != null) {
            builder.setHeader(HEADER_NATIVE_MESSAGE, nativeMessage);
        }
        return builder.build();
    }

    /**
     * 构建 Message（含完整元数据）。
     *
     * @param payload       载荷
     * @param headers       header map
     * @param messageId     消息 ID
     * @param correlationId 关联 ID
     * @param nativeMessage 原生消息
     * @param <T>           载荷类型
     * @return Message 实例
     */
    public static <T> Message<T> of(T payload, Map<String, Object> headers,
                                     String messageId, String correlationId,
                                     Object nativeMessage) {
        MessageBuilder<T> builder = MessageBuilder.withPayload(payload);
        if (headers != null && !headers.isEmpty()) {
            builder.copyHeaders(headers);
        }
        if (messageId != null) {
            builder.setHeader(MessageHeaders.ID, UUID.fromString(messageId.contains("-") ? messageId : padUUID(messageId)));
        }
        if (correlationId != null) {
            builder.setHeader(HEADER_CORRELATION_ID, correlationId);
        }
        if (nativeMessage != null) {
            builder.setHeader(HEADER_NATIVE_MESSAGE, nativeMessage);
        }
        return builder.build();
    }

    // ── Header 提取 ──

    /**
     * 提取消息 ID。
     *
     * @param message 消息
     * @return 消息 ID 字符串，不存在时返回 null
     */
    public static String extractMessageId(Message<?> message) {
        if (message == null) {
            return null;
        }
        Object id = message.getHeaders().get(MessageHeaders.ID);
        return id == null ? null : String.valueOf(id);
    }

    /**
     * 提取关联 ID。
     *
     * @param message 消息
     * @return 关联 ID，不存在时返回 null
     */
    public static String extractCorrelationId(Message<?> message) {
        return headerAsString(message, HEADER_CORRELATION_ID);
    }

    /**
     * 提取租户 ID。
     *
     * @param message 消息
     * @return 租户 ID，不存在时返回 null
     */
    public static String extractTenantId(Message<?> message) {
        return headerAsString(message, HEADER_TENANT_ID);
    }

    /**
     * 将短 UUID 补齐为标准 UUID 格式。
     */
    private static String padUUID(String shortId) {
        if (shortId == null || shortId.length() >= 36) {
            return shortId;
        }
        // 32 位无分隔 UUID 补充分隔符
        if (shortId.length() == 32) {
            return shortId.substring(0, 8) + "-" +
                    shortId.substring(8, 12) + "-" +
                    shortId.substring(12, 16) + "-" +
                    shortId.substring(16, 20) + "-" +
                    shortId.substring(20);
        }
        return "00000000-0000-0000-0000-" + String.format("%012d", Long.parseLong(shortId));
    }

}
