package io.ddd4j.mq.ons.spi;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.ons.ack.OnsMessageAcknowledgment;
import io.ddd4j.mq.ons.consumer.OnsConsumerEndpointRegistrar;
import io.ddd4j.mq.ons.publisher.OnsMQEventPublisher;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import io.ddd4j.mq.serialization.MQEventSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Alibaba ONS broker adapter (pure Java, zero Spring).
 */
public class OnsMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final OnsMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final AtomicReference<Producer> producerRef = new AtomicReference<>();
    private final OnsConsumerEndpointRegistrar consumerRegistrar;

    public OnsMQBrokerAdapter(OnsMQProperties properties, Ddd4jMQProperties mqProperties) {
        this(properties, mqProperties, new JsonMQMessageSerialization());
    }

    public OnsMQBrokerAdapter(OnsMQProperties properties, Ddd4jMQProperties mqProperties,
                              MQEventSerialization serialization) {
        this(createAndStartProducer(properties), properties, mqProperties, serialization);
    }

    public OnsMQBrokerAdapter(Producer producer, OnsMQProperties properties,
                              Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.producerRef.set(Objects.requireNonNull(producer, "producer"));
        this.consumerRegistrar = new OnsConsumerEndpointRegistrar(properties);
    }

    private static Producer createAndStartProducer(OnsMQProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (properties.getProducerId() == null || properties.getProducerId().isBlank()) {
            throw new IllegalStateException("OnsMQProperties.producerId is required");
        }
        Producer p = ONSFactory.createProducer(properties.sessionProperties(properties.getProducerId()));
        p.start();
        return p;
    }

    @Override public MQBrokerType brokerType() { return MQBrokerType.ONS; }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new OnsMQEventPublisher(producer(), properties, props == null ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        if (message == null) return null;
        Object m = message.header(OnsMessageAcknowledgment.HEADER_ONS_MESSAGE);
        Object ctx = message.header(OnsMessageAcknowledgment.HEADER_ONS_CONTEXT);
        if (m instanceof Message msg && ctx instanceof com.aliyun.openservices.ons.api.ConsumeContext c) {
            return new OnsMessageAcknowledgment(c, msg);
        }
        return null;
    }

    @Override public boolean supports(MQBrokerType configured) { return MQBrokerType.ONS == configured; }

    @Override
    public void close() {
        Producer p = producerRef.get();
        if (p != null) {
            try { p.shutdown(); } finally { producerRef.set(null); }
        }
    }

    private Producer producer() { return producerRef.get(); }
}
