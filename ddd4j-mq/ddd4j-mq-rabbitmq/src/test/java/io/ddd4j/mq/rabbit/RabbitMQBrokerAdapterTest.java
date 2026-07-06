package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import io.ddd4j.core.event.MQEvent;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.serialization.JsonSerialization;
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
class RabbitBrokerAdapterTest {

    @Test
    void supportsRabbitBrokerType() {
        RabbitBrokerAdapter adapter = new RabbitBrokerAdapter(new RabbitMQProperties(), new MQProperties());

        assertTrue(adapter.supports(BrokerType.RABBIT));
        assertEquals(BrokerType.RABBIT, adapter.brokerType());
    }

    @Test
    void publisherShouldUseResolvedRoutingKey() throws Exception {
        Channel channel = mock(Channel.class);
        RabbitMQProperties rabbitProperties = new RabbitMQProperties();
        MQProperties mqProperties = new MQProperties();
        mqProperties.setNamespace("sales");
        RabbitEventPublisher publisher = new RabbitEventPublisher(
                () -> channel,
                rabbitProperties,
                mqProperties,
                new JsonSerialization());
        MQEvent event = new MQEvent();
        event.setTag("paid");

        publisher.publish(event, Destination.of("order", "paid"));

        verify(channel).exchangeDeclare(eq("ddd4j.mq"), eq(BuiltinExchangeType.TOPIC), eq(true));
        verify(channel).basicPublish(eq("ddd4j.mq"), eq("sales.order.paid"), any(AMQP.BasicProperties.class), any(byte[].class));
    }

    @Test
    void manualAckShouldMapToChannelOperations() throws Exception {
        Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        RabbitAcknowledgment ack = new RabbitAcknowledgment(channel, 7L, "msg-1", "corr-1");

        ack.ack(false);

        verify(channel).basicAck(7L, false);
        assertTrue(ack.isAcknowledged());
    }
}
