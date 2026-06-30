package io.ddd4j.mq.pulsar.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.pulsar.ack.PulsarMessageAcknowledgment;
import io.ddd4j.mq.pulsar.consumer.PulsarMQConsumerEndpointRegistrar;
import io.ddd4j.mq.pulsar.publisher.PulsarMQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import org.apache.pulsar.client.api.PulsarClient;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Apache Pulsar broker adapter (pure Java, zero Spring).
 */
public class PulsarMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final PulsarMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final AtomicReference<PulsarClient> clientRef = new AtomicReference<>();
    private final PulsarMQConsumerEndpointRegistrar consumerRegistrar;

    public PulsarMQBrokerAdapter(PulsarMQProperties properties, Ddd4jMQProperties mqProperties) {
        this(properties, mqProperties, new JsonMQMessageSerialization());
    }

    public PulsarMQBrokerAdapter(PulsarMQProperties properties, Ddd4jMQProperties mqProperties,
                                 MQEventSerialization serialization) {
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

    public PulsarMQBrokerAdapter(PulsarClient client, PulsarMQProperties properties,
                                 Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.clientRef.set(Objects.requireNonNull(client, "client"));
        this.consumerRegistrar = new PulsarMQConsumerEndpointRegistrar(client, properties);
    }

    @Override public MQBrokerType brokerType() { return MQBrokerType.PULSAR; }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new PulsarMQEventPublisher(client(), properties, props == null ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        if (message == null) return null;
        Object pulsarMsg = message.header(PulsarMessageAcknowledgment.HEADER_PULSAR_MESSAGE);
        if (pulsarMsg instanceof org.apache.pulsar.client.api.Message<?> m) {
            Object idObj = message.header(PulsarMessageAcknowledgment.HEADER_PULSAR_MESSAGE_ID);
            String id = idObj == null ? null : String.valueOf(idObj);
            Object consumerObj = message.header(PulsarMessageAcknowledgment.HEADER_PULSAR_CONSUMER);
            org.apache.pulsar.client.api.Consumer<?> c = consumerObj instanceof org.apache.pulsar.client.api.Consumer<?> cons ? cons : null;
            if (c != null) {
                return new PulsarMessageAcknowledgment(c, m, id, null);
            }
        }
        return null;
    }

    @Override public boolean supports(MQBrokerType configured) { return MQBrokerType.PULSAR == configured; }

    @Override
    public void close() throws Exception {
        PulsarClient c = clientRef.get();
        if (c != null) {
            try { c.close(); } finally { clientRef.set(null); }
        }
    }

    private PulsarClient client() { return clientRef.get(); }
}
