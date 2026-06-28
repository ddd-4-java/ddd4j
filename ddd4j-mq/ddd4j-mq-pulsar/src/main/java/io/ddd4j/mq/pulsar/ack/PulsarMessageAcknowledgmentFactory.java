package io.ddd4j.mq.pulsar.ack;

import io.ddd4j.mq.contract.MQMessage;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 从纯 Java {@link MQMessage} 头信息构建 {@link PulsarMessageAcknowledgment}。
 *
 * <p>2.0.x 重构：彻底移除对 {@code org.springframework.messaging.Message} 的依赖，
 * 直接基于 ddd4j-mq-core 的纯 Java {@link MQMessage} 工作。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class PulsarMessageAcknowledgmentFactory {

    private PulsarMessageAcknowledgmentFactory() {
    }

    /**
     * 从 {@link MQMessage} 头信息解析确认对象。
     *
     * @param message MQ 信封
     * @return 确认对象；缺少必要头时返回 empty
     */
    public static Optional<PulsarMessageAcknowledgment> from(MQMessage<?> message) {
        Objects.requireNonNull(message, "message");
        Map<String, Object> headers = message.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return Optional.empty();
        }

        Object consumerHeader = headers.get(PulsarMessageAcknowledgment.HEADER_PULSAR_CONSUMER);
        Object messageHeader = headers.get(PulsarMessageAcknowledgment.HEADER_PULSAR_MESSAGE);
        if (!(consumerHeader instanceof Consumer<?> consumer) || !(messageHeader instanceof Message<?> pulsarMessage)) {
            return Optional.empty();
        }
        return Optional.of(new PulsarMessageAcknowledgment(consumer, pulsarMessage));
    }
}
