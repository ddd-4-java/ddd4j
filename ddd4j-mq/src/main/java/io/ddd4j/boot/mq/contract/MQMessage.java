package io.ddd4j.boot.mq.contract;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 消息信封：承载业务载荷、头信息、追踪元数据与底层 Broker 原生消息。
 *
 * @param <T> 业务载荷类型
 */
public record MQMessage<T>(
        T payload,
        Map<String, Object> headers,
        String messageId,
        String correlationId,
        Object nativeMessage
) {

    /**
     * 构造消息信封，headers 不可变。
     */
    public MQMessage {
        headers = headers == null ? Map.of() : Collections.unmodifiableMap(headers);
    }

    /**
     * 基于载荷与 messageId 快速构建消息。
     *
     * @param payload   业务载荷
     * @param messageId 消息 ID
     * @param <T>       载荷类型
     * @return 消息信封
     */
    public static <T> MQMessage<T> of(T payload, String messageId) {
        return new MQMessage<>(payload, Map.of(), messageId, null, null);
    }

    /**
     * 完整构建消息信封。
     *
     * @param payload       业务载荷
     * @param headers       消息头
     * @param messageId     消息 ID
     * @param correlationId 关联 ID
     * @param <T>           载荷类型
     * @return 消息信封
     */
    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers, String messageId, String correlationId) {
        return new MQMessage<>(payload, headers, messageId, correlationId, null);
    }

    /**
     * 完整构建消息信封（含原生 Broker 消息）。
     *
     * @param payload       业务载荷
     * @param headers       消息头
     * @param messageId     消息 ID
     * @param correlationId 关联 ID
     * @param nativeMessage 底层 Broker 原生消息
     * @param <T>           载荷类型
     * @return 消息信封
     */
    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers, String messageId,
                                      String correlationId, Object nativeMessage) {
        return new MQMessage<>(payload, headers, messageId, correlationId, nativeMessage);
    }

    /**
     * 读取指定 header。
     *
     * @param key header 键
     * @return header 值，不存在时返回 {@code null}
     */
    public Object header(String key) {
        return headers.get(key);
    }

    /**
     * 读取字符串类型 header。
     *
     * @param key header 键
     * @return 字符串值，不存在或非字符串时返回 {@code null}
     */
    public String headerAsString(String key) {
        Object value = headers.get(key);
        return value == null ? null : Objects.toString(value, null);
    }

    /**
     * 按类型获取底层 Broker 原生消息对象。
     *
     * @param type 目标类型
     * @param <N>  类型参数
     * @return 匹配实例或 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <N> N nativeMessage(Class<N> type) {
        Objects.requireNonNull(type, "type");
        if (nativeMessage != null && type.isInstance(nativeMessage)) {
            return (N) nativeMessage;
        }
        return null;
    }
}
