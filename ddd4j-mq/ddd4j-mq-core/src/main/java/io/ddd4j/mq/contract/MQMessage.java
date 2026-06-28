package io.ddd4j.mq.contract;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 消息信封（纯 Java 契约，零 Spring 依赖）。
 *
 * <p>作为 ddd4j-mq 全模块统一的消息模型：
 * <ul>
 *   <li>各 {@code ddd4j-mq-*} Broker 适配器只需负责把原生消息转换为 {@code MQMessage}；</li>
 *   <li>{@code ddd4j-mq-spring} 提供 Spring {@code org.springframework.messaging.Message} 的桥接；</li>
 *   <li>{@code ddd4j-quarkus} / {@code ddd4j-javalin} 也只需构造纯 Java {@code MQMessage}；</li>
 *   <li>消费侧统一通过 {@link io.ddd4j.mq.registry.MQListenerMethodInvoker} 反射调用方法。</li>
 * </ul>
 *
 * <p>headers 使用 {@code String} / {@code Object} 键值对，与各 Broker 客户端解耦。
 *
 * @param <T> 载荷类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class MQMessage<T> {

    private final T payload;
    private final Map<String, Object> headers;
    private final String messageId;
    private final String correlationId;
    private final Object nativeMessage;

    public MQMessage(T payload, Map<String, Object> headers, String messageId,
                     String correlationId, Object nativeMessage) {
        this.payload = payload;
        this.headers = headers == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(headers));
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.nativeMessage = nativeMessage;
    }

    // ── 访问器 ──

    public T getPayload() {
        return payload;
    }

    public T payload() {
        return payload;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public Map<String, Object> headers() {
        return headers;
    }

    public String getMessageId() {
        return messageId;
    }

    public String messageId() {
        return messageId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String correlationId() {
        return correlationId;
    }

    /**
     * 逃生口：返回底层 Broker 原生消息（如 Kafka RecordMetadata、RabbitMQ Envelope 等）。
     */
    public Object getNativeMessage() {
        return nativeMessage;
    }

    public Object nativeMessage() {
        return nativeMessage;
    }

    public Object header(String key) {
        return headers.get(key);
    }

    public String headerAsString(String key) {
        Object v = headers.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 类型安全地从 nativeMessage 逃生口获取底层对象。
     */
    @SuppressWarnings("unchecked")
    public <N> N nativeMessage(Class<N> type) {
        if (nativeMessage != null && type.isInstance(nativeMessage)) {
            return (N) nativeMessage;
        }
        return null;
    }

    // ── 工厂方法 ──

    public static <T> MQMessage<T> of(T payload) {
        return new MQMessage<>(payload, Collections.emptyMap(), null, null, null);
    }

    public static <T> MQMessage<T> of(T payload, String messageId) {
        return new MQMessage<>(payload, Collections.emptyMap(), messageId, null, null);
    }

    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers, String messageId) {
        return new MQMessage<>(payload, headers, messageId, null, null);
    }

    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers,
                                      String messageId, String correlationId) {
        return new MQMessage<>(payload, headers, messageId, correlationId, null);
    }

    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers,
                                      String messageId, String correlationId, Object nativeMessage) {
        return new MQMessage<>(payload, headers, messageId, correlationId, nativeMessage);
    }

    /**
     * 自动生成 UUID 作为 messageId。
     */
    public static <T> MQMessage<T> autoId(T payload, Map<String, Object> headers) {
        return new MQMessage<>(payload, headers, UUID.randomUUID().toString(), null, null);
    }

    // ── Object ──

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MQMessage<?> that = (MQMessage<?>) o;
        return Objects.equals(payload, that.payload)
                && Objects.equals(headers, that.headers)
                && Objects.equals(messageId, that.messageId)
                && Objects.equals(correlationId, that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payload, headers, messageId, correlationId);
    }

    @Override
    public String toString() {
        return "MQMessage{messageId=" + messageId
                + ", correlationId=" + correlationId
                + ", payload=" + payload
                + ", headers=" + headers + "}";
    }
}
