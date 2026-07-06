package io.ddd4j.mq.rocketmq;

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
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Objects;

/**
 * RocketMQ broker adapter for ddd4j MQ SPI.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RocketBrokerAdapter implements BrokerAdapter, AutoCloseable {

    private final RocketMQProperties rocketProperties;
    private final MQProperties mqProperties;
    private final EventSerialization serialization;
    private final RocketMQConsumerEndpointRegistrar consumerRegistrar;
    private DefaultMQProducer producer;

    public RocketBrokerAdapter(RocketMQProperties rocketProperties, MQProperties mqProperties) {
        this(rocketProperties, mqProperties, new JsonSerialization());
    }

    public RocketBrokerAdapter(
            RocketMQProperties rocketProperties,
            MQProperties mqProperties,
            EventSerialization serialization) {
        this.rocketProperties = Objects.requireNonNull(rocketProperties, "rocketProperties");
        this.mqProperties = Objects.requireNonNull(mqProperties, "mqProperties");
        this.serialization = Objects.requireNonNull(serialization, "serialization");
        this.consumerRegistrar = new RocketMQConsumerEndpointRegistrar(rocketProperties);
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.ROCKET;
    }

    @Override
    public MQEventPublisher createPublisher(MQProperties props) {
        try {
            if (Objects.isNull(producer)) {
                producer = rocketProperties.newProducer();
                if (rocketProperties.isAutoStartProducer()) {
                    producer.start();
                }
            }
            return new RocketMQEventPublisher(producer, Objects.isNull(props) ? mqProperties : props, serialization);
        } catch (Exception ex) {
            throw new IllegalStateException("Create RocketMQ publisher failed", ex);
        }
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
        MessageExt nativeMessage = message.nativeMessage(MessageExt.class);
        if (Objects.isNull(nativeMessage)) {
            Object headerMessage = message.header(RocketAcknowledgment.HEADER_ROCKET_MESSAGE);
            nativeMessage = headerMessage instanceof MessageExt ext ? ext : null;
        }
        return Objects.isNull(nativeMessage) ? null : new RocketAcknowledgment(nativeMessage);
    }

    @Override
    public boolean supports(BrokerType configured) {
        return BrokerType.ROCKET == configured;
    }

    @Override
    public void close() {
        consumerRegistrar.close();
        if (Objects.nonNull(producer)) {
            producer.shutdown();
        }
    }
}
