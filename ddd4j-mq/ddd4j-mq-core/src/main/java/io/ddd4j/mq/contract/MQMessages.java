package io.ddd4j.mq.contract;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 消息工具类（纯 Java，零 Spring 依赖）。
 *
 * <p>提供 {@link MQMessage} 的便捷构建与 header 读取方法。
 * 与历史版本差异：彻底移除对 {@code org.springframework.messaging.Message} 的依赖。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class MQMessages {

    // ── 标准 Header Keys ──

    /** Broker 原生消息逃生口（值：底层 Broker 客户端的原生消息对象） */
    public static final String HEADER_NATIVE_MESSAGE = "ddd4j.native.message";

    /** 消息 ID（标准 UUID 字符串） */
    public static final String HEADER_MESSAGE_ID = "ddd4j.message.id";

    /** 关联 ID（用于链路追踪） */
    public static final String HEADER_CORRELATION_ID = "ddd4j.correlation.id";

    /** 因果 ID（上一个事件的 messageId） */
    public static final String HEADER_CAUSATION_ID = "ddd4j.causation.id";

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

    // ── 便捷读取 ──

    /**
     * 读取 header 值并转为 String。
     */
    public static String headerAsString(MQMessage<?> message, String key) {
        if (Objects.isNull(message) || Objects.isNull(key)) {
            return null;
        }
        Object v = message.header(key);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 读取 header 值。
     */
    public static Object header(MQMessage<?> message, String key) {
        if (Objects.isNull(message) || Objects.isNull(key)) {
            return null;
        }
        return message.header(key);
    }

    /**
     * 返回只读 headers 视图。
     */
    public static Map<String, Object> headers(MQMessage<?> message) {
        if (Objects.isNull(message)) {
            return Collections.emptyMap();
        }
        return message.getHeaders();
    }

    /**
     * 类型安全地从 nativeMessage 逃生口获取底层对象。
     */
    @SuppressWarnings("unchecked")
    public static <N> N nativeMessage(MQMessage<?> message, Class<N> type) {
        if (Objects.isNull(message) || Objects.isNull(type)) {
            return null;
        }
        return message.nativeMessage(type);
    }

    // ── 构建方法 ──

    /**
     * 构建无 header 的 MQMessage。
     */
    public static <T> MQMessage<T> of(T payload) {
        return MQMessage.of(payload);
    }

    /**
     * 构建带 header 的 MQMessage。
     */
    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers) {
        return new MQMessage<>(payload, normalize(headers), null, null, null);
    }

    /**
     * 构建带 header + messageId 的 MQMessage。
     */
    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers, String messageId) {
        return new MQMessage<>(payload, normalize(headers), normalizeMessageId(messageId), null, null);
    }

    /**
     * 构建完整 MQMessage。
     */
    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers,
                                      String messageId, String correlationId, Object nativeMessage) {
        return new MQMessage<>(payload, normalize(headers), normalizeMessageId(messageId),
                correlationId, nativeMessage);
    }

    /**
     * 自动生成 messageId（UUID）。
     */
    public static <T> MQMessage<T> autoId(T payload, Map<String, Object> headers) {
        return new MQMessage<>(payload, normalize(headers), UUID.randomUUID().toString(), null, null);
    }

    // ── Header 提取 ──

    public static String extractMessageId(MQMessage<?> message) {
        if (message == null) {
            return null;
        }
        String id = message.getMessageId();
        if (id != null) {
            return id;
        }
        return headerAsString(message, HEADER_MESSAGE_ID);
    }

    public static String extractCorrelationId(MQMessage<?> message) {
        if (message == null) {
            return null;
        }
        String id = message.getCorrelationId();
        if (id != null) {
            return id;
        }
        return headerAsString(message, HEADER_CORRELATION_ID);
    }

    public static String extractTenantId(MQMessage<?> message) {
        return headerAsString(message, HEADER_TENANT_ID);
    }

    /**
     * 复制 headers 为可变 Map。
     */
    public static Map<String, Object> copyHeaders(MQMessage<?> message) {
        Map<String, Object> source = headers(message);
        if (source.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(source);
    }

    // ── 私有工具 ──

    private static Map<String, Object> normalize(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }
        return new HashMap<>(headers);
    }

    /**
     * 将任何形式的 ID 字符串归一化为标准 UUID 格式。
     */
    private static String normalizeMessageId(String messageId) {
        if (messageId == null) {
            return null;
        }
        if (messageId.length() == 36) {
            return messageId;
        }
        if (messageId.length() == 32) {
            return messageId.substring(0, 8) + "-"
                    + messageId.substring(8, 12) + "-"
                    + messageId.substring(12, 16) + "-"
                    + messageId.substring(16, 20) + "-"
                    + messageId.substring(20);
        }
        try {
            return UUID.fromString(messageId).toString();
        } catch (IllegalArgumentException e) {
            return "00000000-0000-0000-0000-"
                    + String.format("%012d", Math.abs((long) messageId.hashCode()));
        }
    }
}
