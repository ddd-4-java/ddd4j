package io.ddd4j.mq.kafka;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.serialization.JsonSerialization;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka adapter contract tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class KafkaBrokerAdapterTest {

    @Test
    void supportsKafkaBrokerType() {
        KafkaBrokerAdapter adapter = new KafkaBrokerAdapter(new KafkaMQProperties(), new MQProperties());

        assertTrue(adapter.supports(BrokerType.KAFKA));
        assertEquals(BrokerType.KAFKA, adapter.brokerType());
    }

    @Test
    void publisherShouldResolveNamespaceTopicDestination() {
        MQProperties properties = new MQProperties();
        properties.setNamespace("sales");
        properties.setDefaultTopic("default-topic");
        MockProducer<String, String> producer = new MockProducer<>(
                true, null, new StringSerializer(), new StringSerializer());
        KafkaMQEventPublisher publisher = new KafkaMQEventPublisher(producer, properties, new JsonSerialization());
        MQEvent event = new MQEvent();
        event.setTag("paid");

        publisher.publish(event, Destination.of("order", "paid"));

        assertEquals("sales_order", producer.history().get(0).topic());
        assertEquals("paid", producer.history().get(0).key());
    }
}
