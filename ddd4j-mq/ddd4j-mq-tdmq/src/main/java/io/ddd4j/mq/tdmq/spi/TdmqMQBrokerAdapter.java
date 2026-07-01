package io.ddd4j.mq.tdmq.spi;

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
import io.ddd4j.mq.tdmq.ack.TdmqMessageAcknowledgmentFactory;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import io.ddd4j.mq.tdmq.client.TdmqClientPlaceholder;
import io.ddd4j.mq.tdmq.consumer.TdmqMQConsumerEndpointRegistrar;
import io.ddd4j.mq.tdmq.publisher.TdmqMQEventPublisher;

import java.util.Objects;

/**
 * TDMQ broker adapter。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class TdmqMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final TdmqClient tdmqClient;
    private final Ddd4jMQProperties mqProperties;
    private final TdmqMQProperties tdmqProperties;
    private final MQEventSerialization serialization;
    private final TdmqMQConsumerEndpointRegistrar consumerRegistrar;

    public TdmqMQBrokerAdapter(TdmqMQProperties tdmqProperties, Ddd4jMQProperties mqProperties) {
        this(new TdmqClientPlaceholder(), tdmqProperties, mqProperties, new JsonMQMessageSerialization());
    }

    public TdmqMQBrokerAdapter(TdmqClient tdmqClient,
                               TdmqMQProperties tdmqProperties,
                               Ddd4jMQProperties mqProperties) {
        this(tdmqClient, tdmqProperties, mqProperties, new JsonMQMessageSerialization());
    }

    public TdmqMQBrokerAdapter(TdmqClient tdmqClient,
                               TdmqMQProperties tdmqProperties,
                               Ddd4jMQProperties mqProperties,
                               MQEventSerialization serialization) {
        this.tdmqClient = Objects.requireNonNull(tdmqClient, "tdmqClient");
        this.tdmqProperties = Objects.requireNonNull(tdmqProperties, "tdmqProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.consumerRegistrar = new TdmqMQConsumerEndpointRegistrar(tdmqClient, mqProperties, tdmqProperties);
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.TDMQ;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new TdmqMQEventPublisher(
                tdmqClient,
                Objects.isNull(props) ? mqProperties : props,
                serialization);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        return TdmqMessageAcknowledgmentFactory.from(message);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.TDMQ == configured;
    }

    @Override
    public void close() {
        consumerRegistrar.close();
    }
}
