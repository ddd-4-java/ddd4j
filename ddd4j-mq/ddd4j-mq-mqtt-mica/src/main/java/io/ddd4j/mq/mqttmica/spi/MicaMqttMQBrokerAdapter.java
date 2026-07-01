package io.ddd4j.mq.mqttmica.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.mqttmica.ack.MicaMqttMessageAcknowledgment;
import io.ddd4j.mq.mqttmica.consumer.MicaMqttMQConsumerEndpointRegistrar;
import io.ddd4j.mq.mqttmica.publisher.MicaMqttMQEventPublisher;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * mica-mqtt AIO broker adapter (pure Java, zero Spring).
 */
public class MicaMqttMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final MicaMqttProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    private final MicaMqttMQConsumerEndpointRegistrar consumerRegistrar;

    public MicaMqttMQBrokerAdapter(MicaMqttProperties properties, Ddd4jMQProperties mqProperties) {
        this(properties, mqProperties, new JsonMQMessageSerialization());
    }

    public MicaMqttMQBrokerAdapter(MicaMqttProperties properties, Ddd4jMQProperties mqProperties,
                                   MQEventSerialization serialization) {
        this(properties.client(), properties, mqProperties, serialization, true);
    }

    public MicaMqttMQBrokerAdapter(MqttClient client, MicaMqttProperties properties,
                                   Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
        this(client, properties, mqProperties, serialization, true);
    }

    private MicaMqttMQBrokerAdapter(MqttClient client, MicaMqttProperties properties,
                                    Ddd4jMQProperties mqProperties, MQEventSerialization serialization,
                                    boolean requireClient) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        if (requireClient) {
            this.clientRef.set(Objects.requireNonNull(client, "client"));
            this.consumerRegistrar = new MicaMqttMQConsumerEndpointRegistrar(client, properties);
        } else {
            this.consumerRegistrar = null;
        }
    }

    public static MicaMqttMQBrokerAdapter disconnected(MicaMqttProperties properties,
                                                       Ddd4jMQProperties mqProperties,
                                                       MQEventSerialization serialization) {
        return new MicaMqttMQBrokerAdapter(null, properties, mqProperties, serialization, false);
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.MQTT_MICA;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        if (Objects.isNull(client())) {
            throw new IllegalStateException("mica-mqtt client is not initialized");
        }
        return new MicaMqttMQEventPublisher(client(), properties, Objects.isNull(props) ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        if (Objects.isNull(consumerRegistrar)) {
            throw new IllegalStateException("mica-mqtt client is not initialized");
        }
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        if (Objects.isNull(message)) {
            return null;
        }
        Object idObj = message.header(MicaMqttMessageAcknowledgment.HEADER_MICA_MESSAGE_ID);
        Object topicObj = message.header(MicaMqttMessageAcknowledgment.HEADER_MICA_TOPIC);
        if (idObj instanceof Number id && topicObj instanceof String t) {
            return new MicaMqttMessageAcknowledgment(id.longValue(), t, null);
        }
        return null;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.MQTT_MICA == configured;
    }

    @Override
    public void close() {
        MqttClient c = clientRef.get();
        if (Objects.nonNull(c)) {
            try {
                c.stop();
            } finally {
                clientRef.set(null);
            }
        }
    }

    private MqttClient client() {
        return clientRef.get();
    }
}
