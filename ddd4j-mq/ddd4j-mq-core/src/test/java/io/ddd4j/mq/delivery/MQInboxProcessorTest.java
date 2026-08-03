package io.ddd4j.mq.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MQInboxProcessorTest {

    @Test
    void process_shouldHandleFirstDeliveryAndSkipDuplicate() {
        InMemoryInboxStore store = new InMemoryInboxStore();
        MQInboxProcessor processor = new MQInboxProcessor(store, "order-read-model");
        AtomicInteger handled = new AtomicInteger();

        assertTrue(processor.process("message-1", Instant.EPOCH, handled::incrementAndGet));
        assertFalse(processor.process("message-1", Instant.EPOCH, handled::incrementAndGet));

        assertEquals(1, handled.get());
        assertEquals("order-read-model:message-1", store.lastKey);
    }

    @Test
    void process_shouldRejectBlankConsumerAndMessageIdentity() {
        InMemoryInboxStore store = new InMemoryInboxStore();

        assertThrows(IllegalArgumentException.class, () -> new MQInboxProcessor(store, " "));
        MQInboxProcessor processor = new MQInboxProcessor(store, "consumer");
        assertThrows(IllegalArgumentException.class, () -> processor.process("", Instant.EPOCH, () -> {
        }));
    }

    private static final class InMemoryInboxStore implements MQInboxStore {

        private final Set<String> records = new HashSet<>();
        private String lastKey;

        @Override
        public boolean recordIfAbsent(String consumerId, String messageId, Instant processedAt) {
            lastKey = consumerId + ':' + messageId;
            return records.add(lastKey);
        }
    }
}
