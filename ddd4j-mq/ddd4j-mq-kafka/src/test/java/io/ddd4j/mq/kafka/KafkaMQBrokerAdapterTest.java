package io.ddd4j.mq.kafka;

import io.ddd4j.core.event.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.serialization.JsonMQMessageSerialization;
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
class KafkaMQBrokerAdapterTest {

    @Test
    void supportsKafkaBrokerType() {
        KafkaMQBrokerAdapter adapter = new KafkaMQBrokerAdapter(new KafkaMQProperties(), new Ddd4jMQProperties());

        assertTrue(adapter.supports(MQBrokerType.KAFKA));
        assertEquals(MQBrokerType.KAFKA, adapter.brokerType());
    }

    @Test
    void publisherShouldResolveNamespaceTopicDestination() {
        Ddd4jMQProperties properties = new Ddd4jMQProperties();
        properties.setNamespace("sales");
        properties.setDefaultTopic("default-topic");
        MockProducer<String, String> producer = new MockProducer<>(
                true, null, new StringSerializer(), new StringSerializer());
        KafkaMQEventPublisher publisher = new KafkaMQEventPublisher(producer, properties, new JsonMQMessageSerialization());
        MQEvent event = new MQEvent();
        event.setTag("paid");

        publisher.publish(event, MQDestination.of("order", "paid"));

        assertEquals("sales_order", producer.history().get(0).topic());
        assertEquals("paid", producer.history().get(0).key());
    }
}
