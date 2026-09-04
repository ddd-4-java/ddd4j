package io.ddd4j.mq.delivery;

import java.util.Collections;
import java.util.Arrays;
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
        RecordingStore store = new RecordingStore(Arrays.asList(record("message-1", 1)));
        List<String> sent = new ArrayList<>();
        RecordingObserver observer = new RecordingObserver();
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store, message -> sent.add(message.messageId()),
                MQDeliveryPolicy.productionDefault(), observer);

        MQOutboxDispatchResult result = dispatcher.dispatch("instance-a", 10, NOW);

        assertEquals(Collections.singletonList("message-1"), sent);
        assertEquals("instance-a", store.leaseOwner);
        assertEquals(Collections.singletonList("message-1"), store.published);
        assertEquals(new MQOutboxDispatchResult(1, 1, 0, 0, 0), result);
        assertEquals(Collections.singletonList("published"), observer.outboxOutcomes);
    }

    @Test
    void dispatch_shouldRescheduleFailedMessageBeforeMaximumAttempts() {
        RecordingStore store = new RecordingStore(Arrays.asList(record("message-1", 1)));
        RecordingObserver observer = new RecordingObserver();
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store,
                message -> {
                    throw new IllegalStateException("broker unavailable");
                }, MQDeliveryPolicy.productionDefault(), observer);

        MQOutboxDispatchResult result = dispatcher.dispatch("instance-a", 10, NOW);

        assertEquals(Collections.singletonList("message-1"), store.rescheduled);
        assertEquals(new MQOutboxDispatchResult(1, 0, 1, 0, 0), result);
        assertEquals(Collections.singletonList("retry"), observer.outboxOutcomes);
    }

    @Test
    void dispatch_shouldReportDeadMessageAfterMaximumAttempts() {
        RecordingStore store = new RecordingStore(Arrays.asList(record("message-1", 12)));
        RecordingObserver observer = new RecordingObserver();
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store,
                message -> {
                    throw new IllegalStateException("broker unavailable");
                }, MQDeliveryPolicy.productionDefault(), observer);

        MQOutboxDispatchResult result = dispatcher.dispatch("instance-a", 10, NOW);

        assertEquals(new MQOutboxDispatchResult(1, 0, 0, 1, 0), result);
        assertEquals(Collections.singletonList("dead"), observer.outboxOutcomes);
    }

    @Test
    void dispatch_shouldNotifyFailureWhenLeaseConfirmationIsLost() {
        RecordingStore store = new RecordingStore(Arrays.asList(record("message-1", 1)));
        store.publishConfirmed = false;
        RecordingObserver observer = new RecordingObserver();
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store, message -> {
        }, MQDeliveryPolicy.productionDefault(), observer);

        MQOutboxDispatchResult result = dispatcher.dispatch("instance-a", 10, NOW);

        assertEquals(new MQOutboxDispatchResult(1, 0, 0, 0, 1), result);
        assertEquals(Collections.singletonList("failed"), observer.outboxOutcomes);
    }

    @Test
    void dispatch_shouldIgnoreObserverFailure() {
        RecordingStore store = new RecordingStore(Arrays.asList(record("message-1", 1)));
        MQDeliveryObserver failingObserver = new MQDeliveryObserver() {
            @Override
            public void onOutboxPublished(MQOutboxRecord record) {
                throw new IllegalStateException("metrics unavailable");
            }
        };
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store, message -> {
        }, MQDeliveryPolicy.productionDefault(), failingObserver);

        MQOutboxDispatchResult result = dispatcher.dispatch("instance-a", 10, NOW);

        assertEquals(new MQOutboxDispatchResult(1, 1, 0, 0, 0), result);
    }

    @Test
    void dispatch_shouldNotifyFailureWhenRescheduleCannotPersist() {
        RecordingStore store = new RecordingStore(Arrays.asList(record("message-1", 1)));
        store.rescheduleFailure = true;
        RecordingObserver observer = new RecordingObserver();
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store,
                message -> {
                    throw new IllegalStateException("broker unavailable");
                }, MQDeliveryPolicy.productionDefault(), observer);

        assertThrows(IllegalStateException.class, () -> dispatcher.dispatch("instance-a", 10, NOW));

        assertEquals(Collections.singletonList("failed"), observer.outboxOutcomes);
    }

    @Test
    void dispatch_shouldRejectInvalidOwnerAndLimit() {
        RecordingStore store = new RecordingStore(Arrays.asList());
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store, message -> {
        }, MQDeliveryPolicy.productionDefault());

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("", 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("instance-a", 0, NOW));
    }

    private static MQOutboxRecord record(String messageId, int attempts) {
        return new MQOutboxRecord(messageId, "orders.created", "{}", Collections.emptyMap(), MQOutboxStatus.LEASED,
                NOW, "instance-a", NOW.plusSeconds(60), attempts, null, null);
    }

    private static final class RecordingStore implements MQOutboxStore {

        private final List<MQOutboxRecord> claimed;
        private final List<String> published = new ArrayList<>();
        private final List<String> rescheduled = new ArrayList<>();
        private String leaseOwner;
        private boolean publishConfirmed = true;
        private boolean rescheduleFailure;

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
            return publishConfirmed;
        }

        @Override
        public boolean reschedule(String messageId, String leaseOwner, Instant failedAt, String lastError,
                                  MQDeliveryPolicy policy) {
            if (rescheduleFailure) {
                throw new IllegalStateException("outbox unavailable");
            }
            rescheduled.add(messageId);
            return true;
        }

        @Override
        public boolean replay(String messageId, Instant availableAt) {
            return false;
        }
    }

    private static final class RecordingObserver implements MQDeliveryObserver {

        private final List<String> outboxOutcomes = new ArrayList<>();

        @Override
        public void onOutboxPublished(MQOutboxRecord record) {
            outboxOutcomes.add("published");
        }

        @Override
        public void onOutboxRetry(MQOutboxRecord record) {
            outboxOutcomes.add("retry");
        }

        @Override
        public void onOutboxDead(MQOutboxRecord record) {
            outboxOutcomes.add("dead");
        }

        @Override
        public void onOutboxFailed(MQOutboxRecord record) {
            outboxOutcomes.add("failed");
        }
    }
}
