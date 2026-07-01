package io.ddd4j.mq.spring.bridge;

import java.util.Objects;

import io.ddd4j.mq.contract.MQMessage;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spring {@link Message} ↔ 纯 Java {@link MQMessage} 桥接适配器。
 *
 * <p>作为 ddd4j-mq-core 与 Spring 生态的唯一耦合点。
 * <ul>
 *   <li>{@link #fromSpring(Message)}：将 Spring 消息（来自 Spring AMQP / Kafka 客户端）转换为纯 Java 消息信封；</li>
 *   <li>{@link #toSpring(MQMessage)}：将纯 Java 消息信封转换回 Spring 消息，用于发布。</li>
 * </ul>
 *
 * <p>原本散落在 ddd4j-mq-core 各处的 {@code org.springframework.messaging.Message} 引用，
 * 现已全部下沉至本类与 ddd4j-mq-spring 各 Broker 适配器中。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class SpringMessageAdapter {

    private SpringMessageAdapter() {
    }

    /**
     * Spring 消息 → 纯 Java 消息信封。
     *
     * @param springMessage Spring 消息（含 Spring MessageHeaders）
     * @param <T>           载荷类型
     * @return 纯 Java 消息信封
     */
    public static <T> MQMessage<T> fromSpring(Message<T> springMessage) {
        if (Objects.isNull(springMessage)) {
            return null;
        }
        MessageHeaders headers = springMessage.getHeaders();
        T payload = springMessage.getPayload();
        String messageId = extractMessageId(headers);
        String correlationId = extractCorrelationId(headers);
        return new MQMessage<>(payload, headersToMap(headers), messageId, correlationId, springMessage);
    }

    /**
     * 纯 Java 消息信封 → Spring 消息。
     *
     * @param mqMessage 纯 Java 消息信封
     * @param <T>       载荷类型
     * @return Spring 消息
     */
    public static <T> Message<T> toSpring(MQMessage<T> mqMessage) {
        if (Objects.isNull(mqMessage)) {
            return null;
        }
        MessageBuilder<T> builder = MessageBuilder.withPayload(mqMessage.getPayload());
        Map<String, Object> headers = mqMessage.getHeaders();
        if (Objects.nonNull(headers) && !headers.isEmpty()) {
            builder.copyHeaders(headers);
        }
        String messageId = mqMessage.getMessageId();
        if (Objects.nonNull(messageId)) {
            try {
                builder.setHeader(MessageHeaders.ID, UUID.fromString(messageId));
            } catch (IllegalArgumentException ignored) {
                builder.setHeader(MessageHeaders.ID, UUID.randomUUID());
            }
        }
        if (Objects.nonNull(mqMessage.getCorrelationId())) {
            builder.setHeader("ddd4j.correlation.id", mqMessage.getCorrelationId());
        }
        return builder.build();
    }

    // ── 私有工具 ──

    private static Map<String, Object> headersToMap(MessageHeaders headers) {
        if (Objects.isNull(headers)) {
            return new HashMap<>();
        }
        return new HashMap<>(headers);
    }

    private static String extractMessageId(MessageHeaders headers) {
        if (Objects.isNull(headers)) {
            return null;
        }
        Object id = headers.get(MessageHeaders.ID);
        if (Objects.isNull(id)) {
            id = headers.get("ddd4j.message.id");
        }
        return Objects.isNull(id) ? null : String.valueOf(id);
    }

    private static String extractCorrelationId(MessageHeaders headers) {
        if (Objects.isNull(headers)) {
            return null;
        }
        Object id = headers.get("ddd4j.correlation.id");
        return Objects.isNull(id) ? null : String.valueOf(id);
    }
}
