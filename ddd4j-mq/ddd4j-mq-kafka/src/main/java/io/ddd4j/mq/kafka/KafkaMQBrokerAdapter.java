package io.ddd4j.mq.kafka;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Objects;

/**
 * Kafka broker adapter for ddd4j MQ SPI.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class KafkaMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final KafkaMQProperties kafkaProperties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final KafkaMQConsumerEndpointRegistrar consumerRegistrar;

    public KafkaMQBrokerAdapter(KafkaMQProperties kafkaProperties, Ddd4jMQProperties mqProperties) {
        this(kafkaProperties, mqProperties, new JsonMQMessageSerialization());
    }

    public KafkaMQBrokerAdapter(
            KafkaMQProperties kafkaProperties,
            Ddd4jMQProperties mqProperties,
            MQEventSerialization serialization) {
        this.kafkaProperties = Objects.requireNonNull(kafkaProperties, "kafkaProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.consumerRegistrar = new KafkaMQConsumerEndpointRegistrar(kafkaProperties);
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.KAFKA;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new KafkaMQEventPublisher(kafkaProperties, props == null ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        Consumer<?, ?> consumer = message == null ? null : message.nativeMessage(Consumer.class);
        ConsumerRecord<?, ?> record = message == null ? null : message.nativeMessage(ConsumerRecord.class);
        if (consumer == null && message != null) {
            consumer = (Consumer<?, ?>) message.header(KafkaMessageAcknowledgment.HEADER_KAFKA_CONSUMER);
        }
        if (record == null && message != null) {
            record = (ConsumerRecord<?, ?>) message.header(KafkaMessageAcknowledgment.HEADER_KAFKA_RECORD);
        }
        return consumer == null || record == null ? null : new KafkaMessageAcknowledgment(consumer, record);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.KAFKA == configured;
    }

    @Override
    public void close() {
        consumerRegistrar.close();
    }
}
