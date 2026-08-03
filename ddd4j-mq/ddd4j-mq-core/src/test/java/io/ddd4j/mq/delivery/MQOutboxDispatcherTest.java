package io.ddd4j.mq.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MQOutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void dispatch_shouldPublishOutsideStoreAndConfirmWithLeaseOwner() {
        RecordingStore store = new RecordingStore(List.of(record("message-1", 1)));
        List<String> sent = new ArrayList<>();
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store, message -> sent.add(message.messageId()),
                MQDeliveryPolicy.productionDefault());

        MQOutboxDispatchResult result = dispatcher.dispatch("instance-a", 10, NOW);

        assertEquals(List.of("message-1"), sent);
        assertEquals("instance-a", store.leaseOwner);
        assertEquals(List.of("message-1"), store.published);
        assertEquals(new MQOutboxDispatchResult(1, 1, 0, 0, 0), result);
    }

    @Test
    void dispatch_shouldRescheduleFailedMessageBeforeMaximumAttempts() {
        RecordingStore store = new RecordingStore(List.of(record("message-1", 1)));
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store,
                message -> {
                    throw new IllegalStateException("broker unavailable");
                }, MQDeliveryPolicy.productionDefault());

        MQOutboxDispatchResult result = dispatcher.dispatch("instance-a", 10, NOW);

        assertEquals(List.of("message-1"), store.rescheduled);
        assertEquals(new MQOutboxDispatchResult(1, 0, 1, 0, 0), result);
    }

    @Test
    void dispatch_shouldReportDeadMessageAfterMaximumAttempts() {
        RecordingStore store = new RecordingStore(List.of(record("message-1", 12)));
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store,
                message -> {
                    throw new IllegalStateException("broker unavailable");
                }, MQDeliveryPolicy.productionDefault());

        MQOutboxDispatchResult result = dispatcher.dispatch("instance-a", 10, NOW);

        assertEquals(new MQOutboxDispatchResult(1, 0, 0, 1, 0), result);
    }

    @Test
    void dispatch_shouldRejectInvalidOwnerAndLimit() {
        RecordingStore store = new RecordingStore(List.of());
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store, message -> {
        }, MQDeliveryPolicy.productionDefault());

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("", 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("instance-a", 0, NOW));
    }

    private static MQOutboxRecord record(String messageId, int attempts) {
        return new MQOutboxRecord(messageId, "orders.created", "{}", Map.of(), MQOutboxStatus.LEASED,
                NOW, "instance-a", NOW.plusSeconds(60), attempts, null, null);
    }

    private static final class RecordingStore implements MQOutboxStore {

        private final List<MQOutboxRecord> claimed;
        private final List<String> published = new ArrayList<>();
        private final List<String> rescheduled = new ArrayList<>();
        private String leaseOwner;

        private RecordingStore(List<MQOutboxRecord> claimed) {
            this.claimed = claimed;
        }

        @Override
        public void append(MQOutboxRecord record) {
        }

        @Override
        public List<MQOutboxRecord> claim(String leaseOwner, Instant now, int limit, MQDeliveryPolicy policy) {
            this.leaseOwner = leaseOwner;
            return claimed;
        }

        @Override
        public boolean markPublished(String messageId, String leaseOwner, Instant publishedAt) {
            published.add(messageId);
            return true;
        }

        @Override
        public boolean reschedule(String messageId, String leaseOwner, Instant failedAt, String lastError,
                                  MQDeliveryPolicy policy) {
            rescheduled.add(messageId);
            return true;
        }

        @Override
        public boolean replay(String messageId, Instant availableAt) {
            return false;
        }
    }
}
