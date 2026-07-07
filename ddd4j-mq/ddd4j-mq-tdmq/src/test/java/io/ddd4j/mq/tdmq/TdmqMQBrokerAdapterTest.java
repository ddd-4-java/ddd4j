package io.ddd4j.mq.tdmq;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import io.ddd4j.mq.tdmq.ack.TdmqAcknowledgmentFactory;
import io.ddd4j.mq.tdmq.client.TdmqClientPlaceholder;
import io.ddd4j.mq.tdmq.spi.TdmqBrokerAdapter;
import io.ddd4j.mq.tdmq.spi.TdmqMQProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TdmqBrokerAdapterTest {

    private static MQProperties mqProperties() {
        MQProperties properties = new MQProperties();
        properties.setNamespace("demo");
        properties.setDefaultTopic("orders");
        return properties;
    }

    private static ListenerDefinition listenerDefinition() throws NoSuchMethodException {
        Method method = Listener.class.getDeclaredMethod("onMessage", String.class);
        return ListenerDefinition.builder()
                .bean(new Listener())
                .method(method)
                .group("orders-consumer")
                .namespace("demo")
                .topic("orders")
                .tags("paid")
                .concat(".")
                .build();
    }

    @Test
    void supportsTdmqBrokerTypeOnly() {
        TdmqBrokerAdapter adapter = new TdmqBrokerAdapter(new TdmqMQProperties(), mqProperties());

        assertEquals(BrokerType.TDMQ, adapter.brokerType());
        assertTrue(adapter.supports(BrokerType.TDMQ));
        assertFalse(adapter.supports(BrokerType.KAFKA));
    }

    @Test
    void publishesToRegisteredPlaceholderConsumer() throws Exception {
        MQProperties mqProperties = mqProperties();
        TdmqBrokerAdapter adapter = new TdmqBrokerAdapter(
                new TdmqClientPlaceholder(),
                new TdmqMQProperties(),
                mqProperties);
        AtomicReference<Message<?>> received = new AtomicReference<>();
        AtomicBoolean acknowledged = new AtomicBoolean(false);

        adapter.registerConsumer(listenerDefinition(), (message, ack) -> {
            received.set(message);
            ack.ack();
            acknowledged.set(ack.isAcknowledged());
        });

        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setTag("paid");
        event.setNamespace("demo");
        adapter.createPublisher(mqProperties).publish(event, Destination.of("orders", "paid", "demo"));

        assertNotNull(received.get());
        assertTrue(Objects.toString(received.get().payload()).contains("orders"));
        assertTrue(acknowledged.get());
    }

    @Test
    void resolvesAcknowledgmentFromMessageHeaders() {
        AtomicBoolean callbackValue = new AtomicBoolean(false);
        Message<String> message = Message.of(
                "payload",
                Map.of(
                        TdmqAcknowledgmentFactory.HEADER_ACK_CALLBACK,
                        (java.util.function.Consumer<Boolean>) callbackValue::set,
                        TdmqAcknowledgmentFactory.HEADER_DELIVERY_TAG,
                        7L),
                "message-1",
                "correlation-1");

        Acknowledgment ack = new TdmqBrokerAdapter(new TdmqMQProperties(), mqProperties())
                .resolveAcknowledgment(message);

        assertEquals(BrokerType.TDMQ, ack.brokerType());
        assertEquals(7L, ack.deliveryTag());
        ack.nack(true);
        assertTrue(callbackValue.get());
        assertTrue(ack.isAcknowledged());
    }

    private static final class Listener {

        void onMessage(String payload) {
        }
    }
}
