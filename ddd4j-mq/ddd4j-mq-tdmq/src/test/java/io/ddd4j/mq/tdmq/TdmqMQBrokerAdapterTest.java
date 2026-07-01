package io.ddd4j.mq.tdmq;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.tdmq.ack.TdmqMessageAcknowledgmentFactory;
import io.ddd4j.mq.tdmq.client.TdmqClientPlaceholder;
import io.ddd4j.mq.tdmq.spi.TdmqMQBrokerAdapter;
import io.ddd4j.mq.tdmq.spi.TdmqMQProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TdmqMQBrokerAdapterTest {

    @Test
    void supportsTdmqBrokerTypeOnly() {
        TdmqMQBrokerAdapter adapter = new TdmqMQBrokerAdapter(new TdmqMQProperties(), mqProperties());

        assertEquals(MQBrokerType.TDMQ, adapter.brokerType());
        assertTrue(adapter.supports(MQBrokerType.TDMQ));
        assertFalse(adapter.supports(MQBrokerType.KAFKA));
    }

    @Test
    void publishesToRegisteredPlaceholderConsumer() throws Exception {
        Ddd4jMQProperties mqProperties = mqProperties();
        TdmqMQBrokerAdapter adapter = new TdmqMQBrokerAdapter(
                new TdmqClientPlaceholder(),
                new TdmqMQProperties(),
                mqProperties);
        AtomicReference<MQMessage<?>> received = new AtomicReference<>();
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
        adapter.createPublisher(mqProperties).publish(event, MQDestination.of("orders", "paid", "demo"));

        assertNotNull(received.get());
        assertTrue(Objects.toString(received.get().payload()).contains("orders"));
        assertTrue(acknowledged.get());
    }

    @Test
    void resolvesAcknowledgmentFromMessageHeaders() {
        AtomicBoolean callbackValue = new AtomicBoolean(false);
        MQMessage<String> message = MQMessage.of(
                "payload",
                Map.of(
                        TdmqMessageAcknowledgmentFactory.HEADER_ACK_CALLBACK,
                        (java.util.function.Consumer<Boolean>) callbackValue::set,
                        TdmqMessageAcknowledgmentFactory.HEADER_DELIVERY_TAG,
                        7L),
                "message-1",
                "correlation-1");

        MessageAcknowledgment ack = new TdmqMQBrokerAdapter(new TdmqMQProperties(), mqProperties())
                .resolveAcknowledgment(message);

        assertEquals(MQBrokerType.TDMQ, ack.brokerType());
        assertEquals(7L, ack.deliveryTag());
        ack.nack(true);
        assertTrue(callbackValue.get());
        assertTrue(ack.isAcknowledged());
    }

    private static Ddd4jMQProperties mqProperties() {
        Ddd4jMQProperties properties = new Ddd4jMQProperties();
        properties.setNamespace("demo");
        properties.setDefaultTopic("orders");
        return properties;
    }

    private static MQListenerDefinition listenerDefinition() throws NoSuchMethodException {
        Method method = Listener.class.getDeclaredMethod("onMessage", String.class);
        return MQListenerDefinition.builder()
                .bean(new Listener())
                .method(method)
                .group("orders-consumer")
                .namespace("demo")
                .topic("orders")
                .tags("paid")
                .concat(".")
                .build();
    }

    private static final class Listener {

        void onMessage(String payload) {
        }
    }
}
