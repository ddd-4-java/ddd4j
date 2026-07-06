package io.ddd4j.mq.spi;

import io.ddd4j.mq.consume.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.listener.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BrokerAdapters} tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class BrokerAdaptersTest {

    private static MQProperties properties(String broker) {
        MQProperties properties = new MQProperties();
        properties.setBroker(broker);
        return properties;
    }

    @Test
    void shouldRejectUnconfiguredBroker() {
        MQProperties properties = properties("none");

        assertThrows(IllegalStateException.class,
                () -> BrokerAdapters.selectAdapter(List.of(new TestAdapter(BrokerType.KAFKA)), properties));
    }

    @Test
    void shouldRejectEmptyAdapters() {
        MQProperties properties = properties("kafka");

        assertThrows(IllegalStateException.class, () -> BrokerAdapters.selectAdapter(List.of(), properties));
        assertThrows(IllegalStateException.class, () -> BrokerAdapters.selectAdapter(null, properties));
    }

    @Test
    void shouldRejectUnsupportedBroker() {
        MQProperties properties = properties("rabbit");

        assertThrows(IllegalStateException.class,
                () -> BrokerAdapters.selectAdapter(List.of(new TestAdapter(BrokerType.KAFKA)), properties));
    }

    @Test
    void shouldSelectAdapter() {
        MQProperties properties = properties("kafka");
        BrokerAdapter adapter = new TestAdapter(BrokerType.KAFKA);

        BrokerAdapter actual = assertDoesNotThrow(() -> BrokerAdapters.selectAdapter(List.of(adapter), properties));

        assertSame(adapter, actual);
    }

    private static class TestAdapter implements BrokerAdapter {

        private final BrokerType brokerType;

        private TestAdapter(BrokerType brokerType) {
            this.brokerType = brokerType;
        }

        @Override
        public BrokerType brokerType() {
            return brokerType;
        }

        @Override
        public void registerConsumer(ListenerDefinition definition, ConsumerHandler handler) {
        }

        @Override
        public Acknowledgment resolveAcknowledgment(Message<?> message) {
            return null;
        }

        @Override
        public boolean supports(BrokerType configured) {
            return brokerType == configured;
        }
    }
}
