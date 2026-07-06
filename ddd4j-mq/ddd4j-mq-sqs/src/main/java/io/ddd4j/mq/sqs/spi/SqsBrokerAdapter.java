package io.ddd4j.mq.sqs.spi;

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
import io.ddd4j.mq.sqs.ack.SqsAcknowledgment;
import io.ddd4j.mq.sqs.consumer.SqsConsumerEndpointRegistrar;
import io.ddd4j.mq.sqs.publisher.SqsEventPublisher;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AWS SQS Broker 适配器（纯 Java，零 Spring 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SqsBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final SqsMQProperties properties;
    private final MQProperties mqProperties;
    private final EventSerialization serialization;
    private final AtomicReference<SqsClient> clientRef = new AtomicReference<>();
    private final SqsConsumerEndpointRegistrar consumerRegistrar;

    public SqsBrokerAdapter(SqsMQProperties properties, MQProperties mqProperties) {
        this(properties, mqProperties, new JsonSerialization());
    }

    public SqsBrokerAdapter(SqsMQProperties properties, MQProperties mqProperties,
                            EventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.clientRef.set(properties.client());
        this.consumerRegistrar = new SqsConsumerEndpointRegistrar(clientRef.get(), properties);
    }

    public SqsBrokerAdapter(SqsClient client, SqsMQProperties properties,
                            MQProperties mqProperties, EventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.clientRef.set(Objects.requireNonNull(client, "client"));
        this.consumerRegistrar = new SqsConsumerEndpointRegistrar(client, properties);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.SQS;
    }

    @Override
    public EventPublisher createPublisher(MQProperties props) {
        return new SqsEventPublisher(client(), properties, Objects.isNull(props) ? mqProperties : props, serialization);
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
        Object client = message.header(SqsAcknowledgment.HEADER_SQS_CLIENT);
        Object msg = message.header(SqsAcknowledgment.HEADER_SQS_MESSAGE);
        Object queueUrl = message.header(SqsAcknowledgment.HEADER_SQS_QUEUE_URL);
        if (client instanceof SqsClient c && msg instanceof Message m && queueUrl instanceof String q) {
            return new SqsAcknowledgment(c, m, q, properties.isRequeueOnNack());
        }
        return null;
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.SQS == configured;
    }

    @Override
    public void close() {
        SqsClient c = clientRef.get();
        if (Objects.nonNull(c)) {
            try {
                c.close();
            } finally {
                clientRef.set(null);
            }
        }
    }

    private SqsClient client() {
        return clientRef.get();
    }
}
