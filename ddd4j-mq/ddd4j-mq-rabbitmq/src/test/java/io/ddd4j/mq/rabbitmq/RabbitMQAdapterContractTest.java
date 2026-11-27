package io.ddd4j.mq.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** \u901a\u8fc7 <code>mock()</code>\u3001<code>RabbitMQClient.messageId()</code>\u3001<code>acknowledgment.nack()</code> \u9a8c\u8bc1 <code>RabbitMQAdapterContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class RabbitMQAdapterContractTest {

    /**
     * \u9a8c\u8bc1<code>RabbitMQClient.messageId(properties)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002
     * @throws Exception \u88c5\u914d\u8fd0\u884c\u6d4b\u8bd5\u6240\u9700\u7684\u7ec4\u4ef6\u65f6\u5931\u8d25
     */
    @Test
    void shouldPreferStableMessageIdAndNackForRedelivery() throws Exception {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId("native-id")
                .headers(Map.of(MessageHeaders.HEADER_MESSAGE_ID, "stable-id"))
                .build();
        Channel channel = mock(Channel.class);
        RabbitAcknowledgment acknowledgment = new RabbitAcknowledgment(channel, 9L, "stable-id", null);

        assertEquals("stable-id", RabbitMQClient.messageId(properties));
        acknowledgment.nack(true);

        verify(channel).basicNack(9L, false, true);
        assertTrue(acknowledgment.isAcknowledged());
    }
}
