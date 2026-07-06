package io.ddd4j.mq.kafka;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonSerialization;
import io.ddd4j.mq.serialization.EventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Objects;

/**
 * Kafka broker adapter for ddd4j MQ SPI.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class KafkaBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final KafkaMQProperties kafkaProperties;
    private final MQProperties mqProperties;
    private final EventSerialization serialization;
    private final KafkaMQConsumerEndpointRegistrar consumerRegistrar;

    public KafkaBrokerAdapter(KafkaMQProperties kafkaProperties, MQProperties mqProperties) {
        this(kafkaProperties, mqProperties, new JsonSerialization());
    }

    public KafkaBrokerAdapter(
            KafkaMQProperties kafkaProperties,
            MQProperties mqProperties,
            EventSerialization serialization) {
        this.kafkaProperties = Objects.requireNonNull(kafkaProperties, "kafkaProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.consumerRegistrar = new KafkaMQConsumerEndpointRegistrar(kafkaProperties);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.KAFKA;
    }

    @Override
    public EventPublisher createPublisher(MQProperties props) {
        return new KafkaEventPublisher(kafkaProperties, Objects.isNull(props) ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public Acknowledgment resolveAcknowledgment(Message<?> message) {
        Consumer<?, ?> consumer = Objects.isNull(message) ? null : message.nativeMessage(Consumer.class);
        ConsumerRecord<?, ?> record = Objects.isNull(message) ? null : message.nativeMessage(ConsumerRecord.class);
        if (Objects.isNull(consumer) && Objects.nonNull(message)) {
            consumer = (Consumer<?, ?>) message.header(KafkaAcknowledgment.HEADER_KAFKA_CONSUMER);
        }
        if (Objects.isNull(record) && Objects.nonNull(message)) {
            record = (ConsumerRecord<?, ?>) message.header(KafkaAcknowledgment.HEADER_KAFKA_RECORD);
        }
        return Objects.isNull(consumer) || Objects.isNull(record) ? null : new KafkaAcknowledgment(consumer, record);
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.KAFKA == configured;
    }

    @Override
    public void close() {
        consumerRegistrar.close();
    }
}
