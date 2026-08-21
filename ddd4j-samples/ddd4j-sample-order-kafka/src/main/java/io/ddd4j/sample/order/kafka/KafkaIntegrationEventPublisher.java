package io.ddd4j.sample.order.kafka;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.application.IntegrationEventPublisher;
import io.ddd4j.sample.order.application.OutboxMessage;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * 使用 Kafka broker ACK 确认的 Outbox 传输适配器。
 *
 * <p>只有 {@code send(...).get()} 成功返回，{@code OutboxPublisher} 才会将消息标记为已发布；超时和 broker
 * 异常会向上抛出，由 Outbox 保留并重试。
 */
public final class KafkaIntegrationEventPublisher implements IntegrationEventPublisher {

    private final Producer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaIntegrationEventPublisher(Producer<String, String> producer, ObjectMapper objectMapper, String topic) {
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
    }

    @Override
    public void publish(OutboxMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        try {
            String payload = objectMapper.writeValueAsString(message);
            producer.send(new ProducerRecord<>(topic, message.aggregateId(), payload)).get();
        } catch (JacksonException | InterruptedException | ExecutionException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Unable to publish order integration event to Kafka", exception);
        }
    }
}
