package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.core.event.MQEvent;
import io.ddd4j.mq.consume.UnsupportedAckOperationException;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.ons.ack.OnsAcknowledgment;
import io.ddd4j.mq.ons.publisher.OnsEventPublisher;
import io.ddd4j.mq.ons.spi.OnsBrokerAdapter;
import io.ddd4j.mq.ons.spi.OnsMQProperties;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.serialization.EventSerialization;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OnsBrokerAdapterTest {

    @SuppressWarnings("unchecked")
    private static EventSerialization stringSerialization() {
        return new EventSerialization() {
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

    @Test
    void ackShouldExposeListenerReturnAction() {
        Message message = new Message("order", "paid", "key-1", new byte[0]);
        OnsAcknowledgment ack = new OnsAcknowledgment(new ConsumeContext(), message);

        ack.nack(true);

        assertEquals(Action.ReconsumeLater, ack.action());
        assertTrue(ack.isAcknowledged());
        assertThrows(UnsupportedAckOperationException.class, ack::ack);
    }

    @Test
    void publisherShouldBuildOnsMessage() {
        Producer producer = mock(Producer.class);
        OnsMQProperties properties = new OnsMQProperties();
        OnsEventPublisher publisher = new OnsEventPublisher(
                producer, properties, new MQProperties(), stringSerialization());
        MQEvent event = new MQEvent();
        event.setMsgId("msg-1");
        event.setTenantId("tenant-1");

        publisher.publish(event, Destination.of("order", "paid"));

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
        OnsBrokerAdapter adapter = new OnsBrokerAdapter(
                mock(Producer.class), new OnsMQProperties(), new MQProperties(), stringSerialization());

        assertTrue(adapter.supports(BrokerType.ONS));
        assertEquals(BrokerType.ONS, adapter.brokerType());
    }
}
