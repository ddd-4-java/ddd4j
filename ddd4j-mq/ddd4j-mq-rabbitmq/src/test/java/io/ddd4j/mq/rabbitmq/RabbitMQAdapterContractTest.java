package io.ddd4j.mq.rabbitmq;

import java.util.Collections;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitMQAdapterContractTest {

    @Test
    void shouldPreferStableMessageIdAndNackForRedelivery() throws Exception {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId("native-id")
                .headers(Collections.singletonMap(MessageHeaders.HEADER_MESSAGE_ID, "stable-id"))
                .build();
        Channel channel = mock(Channel.class);
        RabbitAcknowledgment acknowledgment = new RabbitAcknowledgment(channel, 9L, "stable-id", null);

        assertEquals("stable-id", RabbitMQClient.messageId(properties));
        acknowledgment.nack(true);

        verify(channel).basicNack(9L, false, true);
        assertTrue(acknowledgment.isAcknowledged());
    }
}
