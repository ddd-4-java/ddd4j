package io.ddd4j.mq.pulsar.spi;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.pulsar.ack.PulsarAcknowledgment;
import io.ddd4j.mq.pulsar.consumer.PulsarMQConsumerEndpointRegistrar;
import io.ddd4j.mq.pulsar.publisher.PulsarEventPublisher;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonSerialization;
import io.ddd4j.mq.serialization.EventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;
import org.apache.pulsar.client.api.PulsarClient;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Apache Pulsar Broker 适配器（纯 Java，零 Spring 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PulsarBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final PulsarMQProperties properties;
    private final MQProperties mqProperties;
    private final EventSerialization serialization;
    private final AtomicReference<PulsarClient> clientRef = new AtomicReference<>();
    private final PulsarMQConsumerEndpointRegistrar consumerRegistrar;

    public PulsarBrokerAdapter(PulsarMQProperties properties, MQProperties mqProperties) {
        this(properties, mqProperties, new JsonSerialization());
    }

    public PulsarBrokerAdapter(PulsarMQProperties properties, MQProperties mqProperties,
                                 EventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        try {
            this.clientRef.set(properties.client());
        } catch (Exception ex) {
            throw new IllegalStateException("Init Pulsar client failed", ex);
        }
        this.consumerRegistrar = new PulsarMQConsumerEndpointRegistrar(clientRef.get(), properties);
    }

    public PulsarBrokerAdapter(PulsarClient client, PulsarMQProperties properties,
                                 MQProperties mqProperties, EventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.clientRef.set(Objects.requireNonNull(client, "client"));
        this.consumerRegistrar = new PulsarMQConsumerEndpointRegistrar(client, properties);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.PULSAR;
    }

    @Override
    public EventPublisher createPublisher(MQProperties props) {
        return new PulsarEventPublisher(client(), properties, Objects.isNull(props) ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public Acknowledgment resolveAcknowledgment(Message<?> message) {
        if (Objects.isNull(message)) {
            return null;
        }
        Object pulsarMsg = message.header(PulsarAcknowledgment.HEADER_PULSAR_MESSAGE);
        if (pulsarMsg instanceof org.apache.pulsar.client.api.Message<?> m) {
            Object idObj = message.header(PulsarAcknowledgment.HEADER_PULSAR_MESSAGE_ID);
            String id = Objects.isNull(idObj) ? null : String.valueOf(idObj);
            Object consumerObj = message.header(PulsarAcknowledgment.HEADER_PULSAR_CONSUMER);
            org.apache.pulsar.client.api.Consumer<?> c = consumerObj instanceof org.apache.pulsar.client.api.Consumer<?> cons ? cons : null;
            if (Objects.nonNull(c)) {
                return new PulsarAcknowledgment(c, m, id, null);
            }
        }
        return null;
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.PULSAR == configured;
    }

    @Override
    public void close() throws Exception {
        PulsarClient c = clientRef.get();
        if (Objects.nonNull(c)) {
            try {
                c.close();
            } finally {
                clientRef.set(null);
            }
        }
    }

    private PulsarClient client() {
        return clientRef.get();
    }
}
