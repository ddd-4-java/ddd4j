package io.ddd4j.mq.ons.spi;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.mq.consume.ack.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.ons.ack.OnsAcknowledgment;
import io.ddd4j.mq.ons.consumer.OnsConsumerEndpointRegistrar;
import io.ddd4j.mq.ons.publisher.OnsMQEventPublisher;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阿里云 ONS Broker 适配器（纯 Java，零 Spring 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OnsBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final OnsMQProperties properties;
    private final MQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final AtomicReference<Producer> producerRef = new AtomicReference<>();
    private final OnsConsumerEndpointRegistrar consumerRegistrar;

    public OnsBrokerAdapter(OnsMQProperties properties, MQProperties mqProperties) {
        this(properties, mqProperties, new JsonMQEventSerialization());
    }

    public OnsBrokerAdapter(OnsMQProperties properties, MQProperties mqProperties,
                              MQEventSerialization serialization) {
        this(createAndStartProducer(properties), properties, mqProperties, serialization);
    }

    public OnsBrokerAdapter(Producer producer, OnsMQProperties properties,
                              MQProperties mqProperties, MQEventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.producerRef.set(Objects.requireNonNull(producer, "producer"));
        this.consumerRegistrar = new OnsConsumerEndpointRegistrar(properties);
    }

    private static Producer createAndStartProducer(OnsMQProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (Objects.isNull(properties.getProducerId()) || io.ddd4j.kit.lang.StrKit.isBlank(properties.getProducerId())) {
            throw new IllegalStateException("OnsMQProperties.producerId is required");
        }
        Producer p = ONSFactory.createProducer(properties.sessionProperties(properties.getProducerId()));
        p.start();
        return p;
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.ONS;
    }

    @Override
    public MQEventPublisher createPublisher(MQProperties props) {
        return new OnsMQEventPublisher(producer(), properties, Objects.isNull(props) ? mqProperties : props, serialization);
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
        Object m = message.header(OnsAcknowledgment.HEADER_ONS_MESSAGE);
        Object ctx = message.header(OnsAcknowledgment.HEADER_ONS_CONTEXT);
        if (m instanceof Message msg && ctx instanceof com.aliyun.openservices.ons.api.ConsumeContext c) {
            return new OnsAcknowledgment(c, msg);
        }
        return null;
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.ONS == configured;
    }

    @Override
    public void close() {
        Producer p = producerRef.get();
        if (Objects.nonNull(p)) {
            try {
                p.shutdown();
            } finally {
                producerRef.set(null);
            }
        }
    }

    private Producer producer() {
        return producerRef.get();
    }
}
