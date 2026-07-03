package io.ddd4j.mq.sqs.spi;

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
import io.ddd4j.mq.sqs.ack.SqsMessageAcknowledgment;
import io.ddd4j.mq.sqs.consumer.SqsConsumerEndpointRegistrar;
import io.ddd4j.mq.sqs.publisher.SqsMQEventPublisher;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AWS SQS Broker 适配器（纯 Java，零 Spring 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SqsBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final SqsMQProperties properties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final AtomicReference<SqsClient> clientRef = new AtomicReference<>();
    private final SqsConsumerEndpointRegistrar consumerRegistrar;

    public SqsBrokerAdapter(SqsMQProperties properties, Ddd4jMQProperties mqProperties) {
        this(properties, mqProperties, new JsonMQMessageSerialization());
    }

    public SqsBrokerAdapter(SqsMQProperties properties, Ddd4jMQProperties mqProperties,
                            MQEventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.clientRef.set(properties.client());
        this.consumerRegistrar = new SqsConsumerEndpointRegistrar(clientRef.get(), properties);
    }

    public SqsBrokerAdapter(SqsClient client, SqsMQProperties properties,
                            Ddd4jMQProperties mqProperties, MQEventSerialization serialization) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.clientRef.set(Objects.requireNonNull(client, "client"));
        this.consumerRegistrar = new SqsConsumerEndpointRegistrar(client, properties);
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.SQS;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new SqsMQEventPublisher(client(), properties, Objects.isNull(props) ? mqProperties : props, serialization);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        if (Objects.isNull(message)) {
            return null;
        }
        Object client = message.header(SqsMessageAcknowledgment.HEADER_SQS_CLIENT);
        Object msg = message.header(SqsMessageAcknowledgment.HEADER_SQS_MESSAGE);
        Object queueUrl = message.header(SqsMessageAcknowledgment.HEADER_SQS_QUEUE_URL);
        if (client instanceof SqsClient c && msg instanceof Message m && queueUrl instanceof String q) {
            return new SqsMessageAcknowledgment(c, m, q, properties.isRequeueOnNack());
        }
        return null;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.SQS == configured;
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
