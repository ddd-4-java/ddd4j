package io.ddd4j.mq.delivery;

import java.util.Collections;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        RecordingObserver observer = new RecordingObserver();
        MQInboxProcessor processor = new MQInboxProcessor(store, "order-read-model", observer);
        AtomicInteger handled = new AtomicInteger();

        assertTrue(processor.process("message-1", Instant.EPOCH, handled::incrementAndGet));
        assertFalse(processor.process("message-1", Instant.EPOCH, handled::incrementAndGet));

        assertEquals(1, handled.get());
        assertEquals("order-read-model:message-1", store.lastKey);
        assertEquals(Arrays.asList("processed", "duplicate"), observer.inboxOutcomes);
    }

    @Test
    void process_shouldRejectBlankConsumerAndMessageIdentity() {
        InMemoryInboxStore store = new InMemoryInboxStore();

        assertThrows(IllegalArgumentException.class, () -> new MQInboxProcessor(store, " "));
        MQInboxProcessor processor = new MQInboxProcessor(store, "consumer");
        assertThrows(IllegalArgumentException.class, () -> processor.process("", Instant.EPOCH, () -> {
        }));
    }

    @Test
    void process_shouldNotifyFailureAndRethrowBusinessException() {
        InMemoryInboxStore store = new InMemoryInboxStore();
        RecordingObserver observer = new RecordingObserver();
        MQInboxProcessor processor = new MQInboxProcessor(store, "consumer", observer);

        assertThrows(IllegalStateException.class,
                () -> processor.process("message-1", Instant.EPOCH,
                        () -> {
                            throw new IllegalStateException("business failed");
                        }));

        assertEquals(Collections.singletonList("failed"), observer.inboxOutcomes);
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

    private static final class RecordingObserver implements MQDeliveryObserver {

        private final List<String> inboxOutcomes = new ArrayList<>();

        @Override
        public void onInboxProcessed(String consumerId, String messageId) {
            inboxOutcomes.add("processed");
        }

        @Override
        public void onInboxDuplicate(String consumerId, String messageId) {
            inboxOutcomes.add("duplicate");
        }

        @Override
        public void onInboxFailed(String consumerId, String messageId) {
            inboxOutcomes.add("failed");
        }
    }
}
