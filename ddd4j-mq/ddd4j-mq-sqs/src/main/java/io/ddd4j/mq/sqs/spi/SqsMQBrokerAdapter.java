package io.ddd4j.mq.sqs.spi;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import io.ddd4j.mq.sqs.ack.SqsMessageAcknowledgment;
import io.ddd4j.mq.sqs.ack.SqsMessageAcknowledgmentFactory;
import io.ddd4j.mq.sqs.consumer.SqsMQConsumerEndpointRegistrar;
import io.ddd4j.mq.sqs.publisher.SqsMQEventPublisher;
import lombok.RequiredArgsConstructor;

/**
 * AWS SQS Broker 适配器，桥接 ddd4j MQ SPI 与 aws-java-sdk-sqs。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RequiredArgsConstructor
public class SqsMQBrokerAdapter implements MQBrokerAdapter {

    private final AmazonSQS amazonSqs;
    private final String defaultQueueUrl;
    private final Ddd4jMQProperties properties;
    private final SqsMQConsumerEndpointRegistrar consumerEndpointRegistrar;

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.SQS;
    }

    @Override
    public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        return new SqsMQEventPublisher(amazonSqs, defaultQueueUrl, props);
    }

    @Override
    public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        consumerEndpointRegistrar.register(definition, handler);
    }

    @Override
    public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        // 逻辑块：优先从 SQS 原生 Message 解析确认
        Message sqsMessage = message.nativeMessage(Message.class);
        if (sqsMessage != null) {
            return SqsMessageAcknowledgmentFactory.from(message)
                    .map(ack -> (MessageAcknowledgment) ack)
                    .orElse(null);
        }
        SqsMessageAcknowledgment sqsAck = message.nativeMessage(SqsMessageAcknowledgment.class);
        return sqsAck;
    }

    @Override
    public boolean supports(MQBrokerType configured) {
        return MQBrokerType.SQS == configured;
    }

    /**
     * 返回当前 MQ 配置。
     */
    public Ddd4jMQProperties properties() {
        return properties;
    }
}
