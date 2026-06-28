package io.ddd4j.mq.pulsar.spi;

import io.ddd4j.mq.pulsar.ack.PulsarMessageAcknowledgment;
import io.ddd4j.mq.pulsar.ack.PulsarMessageAcknowledgmentFactory;
import io.ddd4j.mq.pulsar.consumer.PulsarConsumerEndpointRegistrar;
import io.ddd4j.mq.pulsar.publisher.PulsarMQEventPublisher;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.pulsar.core.PulsarTemplate;

/**
 * Pulsar Broker 适配器，桥接 ddd4j MQ SPI 与 Spring Pulsar。
 * <p>2.0.x 重构：基于纯 Java {@link MQMessage}，不再依赖 {@code org.springframework.messaging.Message}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class PulsarMQBrokerAdapter implements MQBrokerAdapter {

    private final PulsarTemplate<String> pulsarTemplate;
    private final Ddd4jMQProperties properties;
    private final PulsarConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.PULSAR;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new PulsarMQEventPublisher(pulsarTemplate, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 2.0.x：直接基于纯 Java MQMessage 解析（Pulsar 原生消息通过 nativeMessage 逃生口传入）
        PulsarMessageAcknowledgment pulsarAck = message.nativeMessage(PulsarMessageAcknowledgment.class);
        if (pulsarAck != null) {
            return pulsarAck;
        }
        return PulsarMessageAcknowledgmentFactory.from(message).orElse(null);
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.PULSAR == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
