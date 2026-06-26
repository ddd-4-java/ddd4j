package io.ddd4j.mq.kafka.mq;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka 手动确认适配，基于 {@link Acknowledgment} 与 {@link ConsumerRecord}。
 */
public class KafkaMessageAcknowledgment implements MessageAcknowledgment {

    /** MQMessage headers 中存放 Spring Kafka Acknowledgment 的键 */
    public static final String HEADER_KAFKA_ACK = "kafka.acknowledgment";

    /** MQMessage headers 中存放 ConsumerRecord 的键 */
    public static final String HEADER_KAFKA_RECORD = "kafka.consumerRecord";

    private final Acknowledgment kafkaAck;
    private final ConsumerRecord<?, ?> record;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构建 Kafka 确认适配。
     *
     * @param kafkaAck Spring Kafka 确认对象
     * @param record   消费记录
     */
    public KafkaMessageAcknowledgment(Acknowledgment kafkaAck, ConsumerRecord<?, ?> record) {
        this.kafkaAck = Objects.requireNonNull(kafkaAck, "kafkaAck");
        this.record = Objects.requireNonNull(record, "record");
    }

    @Override
    public long deliveryTag() {
        return record.offset();
    }

    @Override
    public String messageId() {
        Header header = record.headers().lastHeader("messageId");
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    @Override
    public String correlationId() {
        Header header = record.headers().lastHeader("correlationId");
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }

    @Override
    public boolean isOpen() {
        return kafkaAck != null;
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.KAFKA;
    }

    @Override
    public void ack() {
        ack(false);
    }

    /**
     * 提交当前 offset；Kafka 不支持 AMQP multiple 语义，{@code multiple=true} 时仍只确认当前记录。
     *
     * @param multiple 是否批量（Kafka 忽略批量语义）
     */
    @Override
    public void ack(boolean multiple) {
        if (acknowledged.compareAndSet(false, true)) {
            kafkaAck.acknowledge();
        }
    }

    /**
     * {@code requeue=true} 时不 commit offset；{@code requeue=false} 时 commit（配合 DLT 策略）。
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /**
     * nack 映射：requeue 时不 ack；不 requeue 时 commit offset。
     *
     * @param multiple  是否批量（Kafka 忽略）
     * @param requeue   是否重新入队
     */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (!requeue) {
            ack(multiple);
        }
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    /**
     * Kafka 不支持 basicRecover 语义。
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void recover(boolean requeue) {
        throw new UnsupportedAckOperationException(MQBrokerType.KAFKA, "recover");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType == null) {
            return Optional.empty();
        }
        if (nativeType.isInstance(kafkaAck)) {
            return Optional.of(nativeType.cast(kafkaAck));
        }
        if (nativeType.isInstance(record)) {
            return Optional.of(nativeType.cast(record));
        }
        return Optional.empty();
    }

    /**
     * 底层 Spring Kafka 确认对象。
     *
     * @return Acknowledgment
     */
    public Acknowledgment kafkaAck() {
        return kafkaAck;
    }

    /**
     * 底层消费记录。
     *
     * @return ConsumerRecord
     */
    public ConsumerRecord<?, ?> record() {
        return record;
    }
}
