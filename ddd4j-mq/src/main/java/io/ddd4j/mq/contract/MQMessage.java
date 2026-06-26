package io.ddd4j.mq.contract;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 消息信封适配器：内部持有 {@link Message}，提供与旧 API 兼容的便捷方法。
 * <p>
 * 新代码应直接使用 {@link Message} + {@link MQMessages} 工具类。
 * 本类用于兼容已有实现模块，可逐步迁移。
 *
 * @param <T> 载荷类型
 * @deprecated 使用 {@link Message} + {@link MQMessages} 替代
 */
public class MQMessage<T> {

    private final Message<T> delegate;

    public MQMessage(T payload, Map<String, Object> headers, String messageId, String correlationId, Object nativeMessage) {
        MessageBuilder<T> builder = MessageBuilder.withPayload(payload);
        if (headers != null && !headers.isEmpty()) {
            builder.copyHeaders(headers);
        }
        if (messageId != null) {
            try {
                builder.setHeader(MessageHeaders.ID, UUID.fromString(messageId.contains("-") ? messageId : padUUID(messageId)));
            } catch (IllegalArgumentException ignored) {
                builder.setHeader(MessageHeaders.ID, UUID.randomUUID());
            }
        }
        if (correlationId != null) {
            builder.setHeader(MQMessages.HEADER_CORRELATION_ID, correlationId);
        }
        if (nativeMessage != null) {
            builder.setHeader(MQMessages.HEADER_NATIVE_MESSAGE, nativeMessage);
        }
        this.delegate = builder.build();
    }

    /**
     * 从已有的 {@link Message} 构造适配器。
     */
    public MQMessage(Message<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    public T getPayload() {
        return delegate.getPayload();
    }

    public T payload() {
        return delegate.getPayload();
    }

    public Map<String, Object> getHeaders() {
        return delegate.getHeaders();
    }

    public Map<String, Object> headers() {
        return delegate.getHeaders();
    }

    public String getMessageId() {
        return MQMessages.extractMessageId(delegate);
    }

    public String getCorrelationId() {
        return MQMessages.extractCorrelationId(delegate);
    }

    public Object getNativeMessage() {
        return delegate.getHeaders().get(MQMessages.HEADER_NATIVE_MESSAGE);
    }

    public Object header(String key) {
        return delegate.getHeaders().get(key);
    }

    public String headerAsString(String key) {
        return MQMessages.headerAsString(delegate, key);
    }

    public <N> N nativeMessage(Class<N> type) {
        return MQMessages.nativeMessage(delegate, type);
    }

    /**
     * 获取底层 {@link Message} 实例。
     */
    public Message<T> toMessage() {
        return delegate;
    }

    // ── 工厂方法 ──

    public static <T> MQMessage<T> of(T payload, String messageId) {
        return new MQMessage<>(payload, Collections.emptyMap(), messageId, null, null);
    }

    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers, String messageId, String correlationId) {
        return new MQMessage<>(payload, headers, messageId, correlationId, null);
    }

    public static <T> MQMessage<T> of(T payload, Map<String, Object> headers, String messageId, String correlationId, Object nativeMessage) {
        return new MQMessage<>(payload, headers, messageId, correlationId, nativeMessage);
    }

    /**
     * 从 {@link Message} 构造 MQMessage。
     */
    public static <T> MQMessage<T> from(Message<T> message) {
        return new MQMessage<>(message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MQMessage<?> that = (MQMessage<?>) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    @Override
    public String toString() {
        return "MQMessage{payload=" + delegate.getPayload() + ", headers=" + delegate.getHeaders() + "}";
    }

    private static String padUUID(String shortId) {
        if (shortId == null || shortId.length() >= 36) {
            return shortId;
        }
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
