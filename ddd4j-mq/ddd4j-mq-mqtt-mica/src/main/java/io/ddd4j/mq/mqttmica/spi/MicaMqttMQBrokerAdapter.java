package io.ddd4j.mq.mqttmica.spi;

import io.ddd4j.mq.consume.ack.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.mqttmica.ack.MicaMqttAcknowledgment;
import io.ddd4j.mq.mqttmica.consumer.MicaMqttMQConsumerEndpointRegistrar;
import io.ddd4j.mq.mqttmica.publisher.MicaMqttMQEventPublisher;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;
import org.dromara.mica.mqtt.core.client.MqttClient;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * mica-mqtt AIO Broker 适配器（纯 Java，零 Spring 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MicaMqttBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final MicaMqttProperties properties;
    private final MQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    private final MicaMqttMQConsumerEndpointRegistrar consumerRegistrar;

    public MicaMqttBrokerAdapter(MicaMqttProperties properties, MQProperties mqProperties) {
        this(properties, mqProperties, new JsonMQEventSerialization());
    }

    public MicaMqttBrokerAdapter(MicaMqttProperties properties, MQProperties mqProperties,
                                   MQEventSerialization serialization) {
        this(properties.client(), properties, mqProperties, serialization, true);
    }

    public MicaMqttBrokerAdapter(MqttClient client, MicaMqttProperties properties,
                                   MQProperties mqProperties, MQEventSerialization serialization) {
        this(client, properties, mqProperties, serialization, true);
    }

    private MicaMqttBrokerAdapter(MqttClient client, MicaMqttProperties properties,
                                    MQProperties mqProperties, MQEventSerialization serialization,
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

    public static MicaMqttBrokerAdapter disconnected(MicaMqttProperties properties,
                                                       MQProperties mqProperties,
                                                       MQEventSerialization serialization) {
        return new MicaMqttBrokerAdapter(null, properties, mqProperties, serialization, false);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.MQTT_MICA;
    }

    @Override
    public MQEventPublisher createPublisher(MQProperties props) {
        if (Objects.isNull(client())) {
            throw new IllegalStateException("mica-mqtt client is not initialized");
        }
        return new MicaMqttMQEventPublisher(client(), properties, Objects.isNull(props) ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        if (Objects.isNull(consumerRegistrar)) {
            throw new IllegalStateException("mica-mqtt client is not initialized");
        }
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public Acknowledgment resolveAcknowledgment(Message<?> message) {
        if (Objects.isNull(message)) {
            return null;
        }
        Object idObj = message.header(MicaMqttAcknowledgment.HEADER_MICA_MESSAGE_ID);
        Object topicObj = message.header(MicaMqttAcknowledgment.HEADER_MICA_TOPIC);
        if (idObj instanceof Number id && topicObj instanceof String t) {
            return new MicaMqttAcknowledgment(id.longValue(), t, null);
        }
        return null;
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.MQTT_MICA == configured;
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
