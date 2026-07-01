package io.ddd4j.mq.spi;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MQBrokerAdapters} tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MQBrokerAdaptersTest {

    @Test
    void shouldRejectUnconfiguredBroker() {
        Ddd4jMQProperties properties = properties("none");

        assertThrows(IllegalStateException.class,
                () -> MQBrokerAdapters.createPublisher(List.of(new TestAdapter(MQBrokerType.KAFKA)), properties));
    }

    @Test
    void shouldRejectEmptyAdapters() {
        Ddd4jMQProperties properties = properties("kafka");

        assertThrows(IllegalStateException.class, () -> MQBrokerAdapters.createPublisher(List.of(), properties));
        assertThrows(IllegalStateException.class, () -> MQBrokerAdapters.selectAdapter(null, properties));
    }

    @Test
    void shouldRejectUnsupportedBroker() {
        Ddd4jMQProperties properties = properties("rabbit");

        assertThrows(IllegalStateException.class,
                () -> MQBrokerAdapters.createPublisher(List.of(new TestAdapter(MQBrokerType.KAFKA)), properties));
    }

    @Test
    void shouldRejectMissingPublisherImplementation() {
        Ddd4jMQProperties properties = properties("kafka");

        assertThrows(UnsupportedOperationException.class,
                () -> MQBrokerAdapters.createPublisher(List.of(new TestAdapter(MQBrokerType.KAFKA)), properties));
    }

    @Test
    void shouldRejectNullPublisher() {
        Ddd4jMQProperties properties = properties("kafka");

        assertThrows(IllegalStateException.class,
                () -> MQBrokerAdapters.createPublisher(List.of(new NullPublisherAdapter(MQBrokerType.KAFKA)), properties));
    }

    @Test
    void shouldCreatePublisher() {
        Ddd4jMQProperties properties = properties("kafka");
        MQEventPublisher publisher = new TestPublisher();

        MQEventPublisher actual = assertDoesNotThrow(
                () -> MQBrokerAdapters.createPublisher(List.of(new PublisherAdapter(MQBrokerType.KAFKA, publisher)), properties));

        assertSame(publisher, actual);
    }

    @Test
    void shouldSelectAdapter() {
        Ddd4jMQProperties properties = properties("kafka");
        MQBrokerAdapter adapter = new TestAdapter(MQBrokerType.KAFKA);

        MQBrokerAdapter actual = assertDoesNotThrow(() -> MQBrokerAdapters.selectAdapter(List.of(adapter), properties));

        assertSame(adapter, actual);
    }

    private static Ddd4jMQProperties properties(String broker) {
        Ddd4jMQProperties properties = new Ddd4jMQProperties();
        properties.setBroker(broker);
        return properties;
    }

    private static class TestAdapter implements MQBrokerAdapter {

        private final MQBrokerType brokerType;

        private TestAdapter(MQBrokerType brokerType) {
            this.brokerType = brokerType;
        }

        @Override
        public MQBrokerType brokerType() {
            return brokerType;
        }

        @Override
        public void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler) {
        }

        @Override
        public MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
            return null;
        }

        @Override
        public boolean supports(MQBrokerType configured) {
            return brokerType == configured;
        }
    }

    private static class NullPublisherAdapter extends TestAdapter {

        private NullPublisherAdapter(MQBrokerType brokerType) {
            super(brokerType);
        }

        @Override
        public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
            return null;
        }
    }

    private static class PublisherAdapter extends TestAdapter {

        private final MQEventPublisher publisher;

        private PublisherAdapter(MQBrokerType brokerType, MQEventPublisher publisher) {
            super(brokerType);
            this.publisher = publisher;
        }

        @Override
        public MQEventPublisher createPublisher(Ddd4jMQProperties props) {
            return publisher;
        }
    }

    private static class TestPublisher implements MQEventPublisher {

        @Override
        public <T extends MQEvent> void publish(T event, MQDestination destination) {
        }
    }
}
