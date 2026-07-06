package io.ddd4j.mq.tdmq.spi;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonSerialization;
import io.ddd4j.mq.serialization.EventSerialization;
import io.ddd4j.mq.spi.BrokerAdapter;
import io.ddd4j.mq.tdmq.ack.TdmqAcknowledgmentFactory;
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
public class TdmqBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final TdmqClient tdmqClient;
    private final MQProperties mqProperties;
    private final TdmqMQProperties tdmqProperties;
    private final EventSerialization serialization;
    private final TdmqMQConsumerEndpointRegistrar consumerRegistrar;

    public TdmqBrokerAdapter(TdmqMQProperties tdmqProperties, MQProperties mqProperties) {
        this(new TdmqClientPlaceholder(), tdmqProperties, mqProperties, new JsonSerialization());
    }

    public TdmqBrokerAdapter(TdmqClient tdmqClient,
                               TdmqMQProperties tdmqProperties,
                               MQProperties mqProperties) {
        this(tdmqClient, tdmqProperties, mqProperties, new JsonSerialization());
    }

    public TdmqBrokerAdapter(TdmqClient tdmqClient,
                               TdmqMQProperties tdmqProperties,
                               MQProperties mqProperties,
                               EventSerialization serialization) {
        this.tdmqClient = Objects.requireNonNull(tdmqClient, "tdmqClient");
        this.tdmqProperties = Objects.requireNonNull(tdmqProperties, "tdmqProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.consumerRegistrar = new TdmqMQConsumerEndpointRegistrar(tdmqClient, mqProperties, tdmqProperties);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.TDMQ;
    }

    @Override
    public MQEventPublisher createPublisher(MQProperties props) {
        return new TdmqMQEventPublisher(
                tdmqClient,
                Objects.isNull(props) ? mqProperties : props,
                serialization);
    }

    @Override
    public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        consumerRegistrar.register(definition, handler);
    }

    @Override
    public Acknowledgment resolveAcknowledgment(Message<?> message) {
        return TdmqAcknowledgmentFactory.from(message);
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.TDMQ == configured;
    }

    @Override
    public void close() {
        consumerRegistrar.close();
    }
}
