package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.ons.ack.OnsMessageAcknowledgment;
import io.ddd4j.mq.ons.publisher.OnsMQEventPublisher;
import io.ddd4j.mq.ons.spi.OnsMQBrokerAdapter;
import io.ddd4j.mq.ons.spi.OnsMQProperties;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.serialization.MQEventSerialization;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OnsMQBrokerAdapterTest {

    @Test
    void ackShouldExposeListenerReturnAction() {
        Message message = new Message("order", "paid", "key-1", new byte[0]);
        message.setMsgID("msg-1");
        OnsMessageAcknowledgment ack = new OnsMessageAcknowledgment(new ConsumeContext(), message);

        ack.nack(true);

        assertEquals(Action.ReconsumeLater, ack.action());
        assertTrue(ack.isAcknowledged());
        assertThrows(UnsupportedAckOperationException.class, ack::ack);
    }

    @Test
    void publisherShouldBuildOnsMessage() {
        Producer producer = mock(Producer.class);
        OnsMQProperties properties = new OnsMQProperties();
        OnsMQEventPublisher publisher = new OnsMQEventPublisher(
                producer, properties, new Ddd4jMQProperties(), stringSerialization());
        MQEvent event = new MQEvent();
        event.setMsgId("msg-1");
        event.setTenantId("tenant-1");

        publisher.publish(event, MQDestination.of("order", "paid"));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(captor.capture());
        Message message = captor.getValue();
        assertEquals("order", message.getTopic());
        assertEquals("paid", message.getTag());
        assertEquals("msg-1", message.getKey());
        assertEquals("tenant-1", message.getUserProperties("tenantId"));
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), message.getBody());
    }

    @Test
    void supportsOnsBrokerType() {
        OnsMQBrokerAdapter adapter = new OnsMQBrokerAdapter(
                mock(Producer.class), new OnsMQProperties(), new Ddd4jMQProperties(), stringSerialization());

        assertTrue(adapter.supports(MQBrokerType.ONS));
        assertEquals(MQBrokerType.ONS, adapter.brokerType());
    }

    @SuppressWarnings("unchecked")
    private static MQEventSerialization stringSerialization() {
        return new MQEventSerialization() {
            @Override
            public <S, T> T deserialize(S src, Class<T> dist) {
                return null;
            }

            @Override
            public <T> T serialize(Object src) {
                return (T) "payload";
            }
        };
    }
}
