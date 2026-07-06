package io.ddd4j.mq.mqtt.spi;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.mqtt.ack.MqttAcknowledgment;
import io.ddd4j.mq.mqtt.consumer.MqttConsumerEndpointRegistrar;
import io.ddd4j.mq.mqtt.publisher.MqttEventPublisher;
import io.ddd4j.mq.publish.EventPublisher;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonSerialization;
import io.ddd4j.mq.serialization.EventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Eclipse Paho MQTT Broker 适配器（纯 Java，零 Spring 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MqttBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final MqttMQProperties properties;
    private final MQProperties mqProperties;
    private final EventSerialization serialization;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    private final MqttConsumerEndpointRegistrar consumerRegistrar;

    public MqttBrokerAdapter(MqttMQProperties properties, MQProperties mqProperties) {
        this(properties, mqProperties, new JsonSerialization());
    }

    public MqttBrokerAdapter(MqttMQProperties properties, MQProperties mqProperties,
                               EventSerialization serialization) {
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

    public MqttBrokerAdapter(MqttClient client, MqttMQProperties properties,
                               MQProperties mqProperties, EventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.clientRef.set(Objects.requireNonNull(client, "client"));
        this.consumerRegistrar = new MqttConsumerEndpointRegistrar(client, properties);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.MQTT;
    }

    @Override
    public EventPublisher createPublisher(MQProperties props) {
        return new MqttEventPublisher(client(), properties, Objects.isNull(props) ? mqProperties : props, serialization);
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
        Object msg = message.header(MqttAcknowledgment.HEADER_MQTT_MESSAGE);
        Object topic = message.header(MqttAcknowledgment.HEADER_MQTT_TOPIC);
        if (msg instanceof MqttMessage m && topic instanceof String t) {
            return new MqttAcknowledgment(m, t);
        }
        return null;
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.MQTT == configured;
    }

    @Override
    public void close() throws Exception {
        MqttClient c = clientRef.get();
        if (Objects.nonNull(c)) {
            try {
                c.disconnect();
                c.close();
            } finally {
                clientRef.set(null);
            }
        }
    }

    private MqttClient client() {
        return clientRef.get();
    }
}
