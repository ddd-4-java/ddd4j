package io.ddd4j.mq.rocketmq;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.serialization.JsonSerialization;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * RocketMQ adapter contract tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RocketBrokerAdapterTest {

    private static ListenerDefinition definition(String tags) throws Exception {
        Method method = SampleConsumer.class.getDeclaredMethod("handle", MQEvent.class);
        return ListenerDefinition.builder()
                .bean(new SampleConsumer())
                .method(method)
                .group("sample")
                .namespace("sales")
                .topic("order")
                .tags(tags)
                .supports(java.util.List.of("*"))
                .concat(".")
                .build();
    }

    @Test
    void supportsRocketBrokerType() {
        RocketBrokerAdapter adapter = new RocketBrokerAdapter(new RocketMQProperties(), new MQProperties());

        assertTrue(adapter.supports(BrokerType.ROCKET));
        assertEquals(BrokerType.ROCKET, adapter.brokerType());
    }

    @Test
    void publisherShouldBuildRocketMessage() throws Exception {
        MQProperties properties = new MQProperties();
        properties.setNamespace("sales");
        MQProducer producer = mock(MQProducer.class);
        RocketMQEventPublisher publisher = new RocketMQEventPublisher(producer, properties, new JsonSerialization());
        MQEvent event = new MQEvent();
        event.setTag("paid");

        publisher.publish(event, Destination.of("order", "paid"));

        verify(producer).send(any(Message.class));
        Message message = RocketMQEventPublisher.toMessage(event, Destination.of("order", "paid"), properties, new JsonSerialization());
        assertEquals("sales.order", message.getTopic());
        assertEquals("paid", message.getTags());
    }

    @Test
    void consumerRegistrationShouldSubscribeTopicAndTags() throws Exception {
        RocketMQProperties properties = new RocketMQProperties();
        properties.setAutoStartConsumers(false);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        RocketMQConsumerEndpointRegistrar registrar = new RocketMQConsumerEndpointRegistrar(properties, group -> consumer);

        registrar.register(definition("paid"), (message, ack) -> ack.ackSingle());

        verify(consumer).subscribe(eq("sales.order"), eq("paid"));
        verify(consumer).registerMessageListener(any(MessageListenerConcurrently.class));
    }

    @Test
    void manualAckShouldMapToReconsumeState() {
        MessageExt message = new MessageExt();
        message.setMsgId("msg-1");
        message.setQueueOffset(9L);
        RocketAcknowledgment ack = new RocketAcknowledgment(message);

        ack.nack(true);

        assertTrue(ack.isAcknowledged());
        assertTrue(ack.shouldReconsume());
    }

    static class SampleConsumer {
        void handle(MQEvent event) {
        }
    }
}
