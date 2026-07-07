package io.ddd4j.mq.pulsar.consumer;

import io.ddd4j.mq.consume.MQEventConsumer;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.util.TagMatcher;
import io.ddd4j.mq.pulsar.ack.PulsarAcknowledgment;
import io.ddd4j.mq.pulsar.spi.PulsarMQProperties;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Apache Pulsar 消费者实现（纯 Java，零 Spring 依赖）。
 *
 * <p>实现 {@link MQEventConsumer}，在 {@link #subscribe(MQListener, MQEventCallback)} 中按
 * {@code tenant/namespace/topic[:tag]} 解析物理 topic，建立 Pulsar 原生消费者并注册 messageListener；
 * 收到消息后 TagMatcher 过滤、提取 payload 字符串，构建 {@link PulsarAcknowledgment}，
 * 通过 {@link MQEventCallback} 交给 core 处理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class PulsarMQConsumer implements MQEventConsumer {

    private final PulsarClient client;
    private final PulsarMQProperties properties;

    public PulsarMQConsumer(PulsarClient client, PulsarMQProperties properties) {
        this.client = Objects.requireNonNull(client, "client");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void subscribe(MQListener listener, MQEventCallback onEvent) {
        Objects.requireNonNull(listener, "listener");
        try {
            String topic = properties.physicalTopic(listener.getTopic(), null);
            client.newConsumer(Schema.BYTES)
                    .topic(topic)
                    .subscriptionName(properties.getSubscriptionName() + "-" + listener.namespaceTopicTags())
                    .subscriptionType(SubscriptionType.valueOf(properties.getSubscriptionType()))
                    .negativeAckRedeliveryDelay(properties.getNegativeAckRedeliveryDelayMs(), TimeUnit.MILLISECONDS)
                    .messageListener((consumer, msg) -> handleMessage(consumer, msg, listener, onEvent))
                    .subscribe();
        } catch (Exception ex) {
            throw new IllegalStateException("Subscribe Pulsar consumer failed: " + listener.namespaceTopicTags(), ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void handleMessage(Consumer<byte[]> consumer, Message<byte[]> msg, MQListener listener, MQEventCallback onEvent) {
        try {
            String tag = msg.getProperty(MessageHeaders.HEADER_DESTINATION_TAG);
            if (!TagMatcher.match(tag, listener.getTags())) {
                consumer.acknowledge(msg);
                return;
            }
            String messageId = messageIdString(msg.getMessageId());
            String payload = new String(msg.getValue(), StandardCharsets.UTF_8);
            String tenantId = msg.getProperty(MessageHeaders.HEADER_TENANT_ID);
            Acknowledgment ack = new PulsarAcknowledgment(consumer, msg, messageId, null);
            onEvent.onEvent(payload, messageId, tenantId, tag, ack);
        } catch (Throwable ex) {
            try {
                consumer.negativeAcknowledge(msg);
            } catch (Exception ignore) {
            }
        }
    }

    private static String messageIdString(MessageId id) {
        return Objects.isNull(id) ? null : id.toString();
    }
}
