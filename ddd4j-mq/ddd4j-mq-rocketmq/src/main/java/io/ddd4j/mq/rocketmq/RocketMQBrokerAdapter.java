package io.ddd4j.mq.rocketmq;

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
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Objects;

/**
 * RocketMQ broker adapter for ddd4j MQ SPI.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RocketMQBrokerAdapter implements MQBrokerAdapter, AutoCloseable {

    private final RocketMQProperties rocketProperties;
    private final Ddd4jMQProperties mqProperties;
    private final MQEventSerialization serialization;
    private final RocketMQConsumerEndpointRegistrar consumerRegistrar;
    private DefaultMQProducer producer;

    public RocketMQBrokerAdapter(RocketMQProperties rocketProperties, Ddd4jMQProperties mqProperties) {
        this(rocketProperties, mqProperties, new JsonMQMessageSerialization());
    }

    public RocketMQBrokerAdapter(
            RocketMQProperties rocketProperties,
            Ddd4jMQProperties mqProperties,
            MQEventSerialization serialization) {
        this.rocketProperties = Objects.requireNonNull(rocketProperties, "rocketProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.consumerRegistrar = new RocketMQConsumerEndpointRegistrar(rocketProperties);
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.ROCKET;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        try {
            if (java.util.Objects.isNull(producer)) {
                producer = rocketProperties.newProducer();
                if (rocketProperties.isAutoStartProducer()) {
                    producer.start();
                }
            }
            return new RocketMQEventPublisher(producer, java.util.Objects.isNull(props) ? mqProperties : props, serialization);
        } catch (Exception ex) {
            throw new IllegalStateException("Create RocketMQ publisher failed", ex);
        }
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
        MessageExt nativeMessage = message.nativeMessage(MessageExt.class);
        if (java.util.Objects.isNull(nativeMessage)) {
            Object headerMessage = message.header(RocketMessageAcknowledgment.HEADER_ROCKET_MESSAGE);
            nativeMessage = headerMessage instanceof MessageExt ext ? ext : null;
        }
        return java.util.Objects.isNull(nativeMessage) ? null : new RocketMessageAcknowledgment(nativeMessage);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.ROCKET == configured;
    }

    @Override
    public void close() {
        consumerRegistrar.close();
        if (java.util.Objects.nonNull(producer)) {
            producer.shutdown();
        }
    }
}
