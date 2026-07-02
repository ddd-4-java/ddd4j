package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import io.ddd4j.core.event.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RabbitMQ adapter contract tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class RabbitMQBrokerAdapterTest {

    @Test
    void supportsRabbitBrokerType() {
        RabbitMQBrokerAdapter adapter = new RabbitMQBrokerAdapter(new RabbitMQProperties(), new Ddd4jMQProperties());

        assertTrue(adapter.supports(MQBrokerType.RABBIT));
        assertEquals(MQBrokerType.RABBIT, adapter.brokerType());
    }

    @Test
    void publisherShouldUseResolvedRoutingKey() throws Exception {
        Channel channel = mock(Channel.class);
        RabbitMQProperties rabbitProperties = new RabbitMQProperties();
        Ddd4jMQProperties mqProperties = new Ddd4jMQProperties();
        mqProperties.setNamespace("sales");
        RabbitMQEventPublisher publisher = new RabbitMQEventPublisher(
                () -> channel,
                rabbitProperties,
                mqProperties,
                new JsonMQMessageSerialization());
        MQEvent event = new MQEvent();
        event.setTag("paid");

        publisher.publish(event, MQDestination.of("order", "paid"));

        verify(channel).exchangeDeclare(eq("ddd4j.mq"), eq(BuiltinExchangeType.TOPIC), eq(true));
        verify(channel).basicPublish(eq("ddd4j.mq"), eq("sales.order.paid"), any(AMQP.BasicProperties.class), any(byte[].class));
    }

    @Test
    void manualAckShouldMapToChannelOperations() throws Exception {
        Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        RabbitMessageAcknowledgment ack = new RabbitMessageAcknowledgment(channel, 7L, "msg-1", "corr-1");

        ack.ack(false);

        verify(channel).basicAck(7L, false);
        assertTrue(ack.isAcknowledged());
    }
}
