package io.ddd4j.mq.mqtt.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.mqtt.ack.MqttMessageAcknowledgment;
import io.ddd4j.mq.mqtt.consumer.MqttConsumerEndpointRegistrar;
import io.ddd4j.mq.mqtt.publisher.MqttMQEventPublisher;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Eclipse Paho MQTT broker adapter (pure Java, zero Spring).
 */
public class MqttMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final MqttMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    private final MqttConsumerEndpointRegistrar consumerRegistrar;

    public MqttMQBrokerAdapter(MqttMQProperties properties, Ddd4jMQProperties mqProperties) {
        this(properties, mqProperties, new JsonMQMessageSerialization());
    }

    public MqttMQBrokerAdapter(MqttMQProperties properties, Ddd4jMQProperties mqProperties,
                               MQEventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        try {
            MqttClient c = new MqttClient(properties.getServerUri(), properties.newClientId());
            c.connect(properties.connectOptions());
            this.clientRef.set(c);
        } catch (Exception ex) {
            throw new IllegalStateException("Init MQTT client failed", ex);
        }
        this.consumerRegistrar = new MqttConsumerEndpointRegistrar(clientRef.get(), properties);
    }

    public MqttMQBrokerAdapter(MqttClient client, MqttMQProperties properties,
                               Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.clientRef.set(Objects.requireNonNull(client, "client"));
        this.consumerRegistrar = new MqttConsumerEndpointRegistrar(client, properties);
    }

    @Override public MQBrokerType brokerType() { return MQBrokerType.MQTT; }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new MqttMQEventPublisher(client(), properties, java.util.Objects.isNull(props) ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        if (java.util.Objects.isNull(message)) {
            return null;
        }
        Object msg = message.header(MqttMessageAcknowledgment.HEADER_MQTT_MESSAGE);
        Object topic = message.header(MqttMessageAcknowledgment.HEADER_MQTT_TOPIC);
        if (msg instanceof MqttMessage m && topic instanceof String t) {
            return new MqttMessageAcknowledgment(m, t);
        }
        return null;
    }

    @Override public boolean supports(MQBrokerType configured) { return MQBrokerType.MQTT == configured; }

    @Override
    public void close() throws Exception {
        MqttClient c = clientRef.get();
        if (java.util.Objects.nonNull(c)) {
            try { c.disconnect(); c.close(); } finally { clientRef.set(null); }
        }
    }

    private MqttClient client() { return clientRef.get(); }
}
