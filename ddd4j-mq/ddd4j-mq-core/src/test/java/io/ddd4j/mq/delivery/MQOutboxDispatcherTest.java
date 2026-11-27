/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.mq.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** \u9a8c\u8bc1 <code>MQOutboxDispatcher</code> \u5728<code>dispatch</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class MQOutboxDispatcherTest {

    /** \u7528\u6765\u56fa\u5b9a\u4e8b\u4ef6\u6216\u4f4d\u70b9\u65f6\u95f4\u7684\u6d4b\u8bd5\u65f6\u95f4\u70b9\u3002 */
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    /**
     * \u8c03\u7528 <code>Collections.emptyMap()</code>\u3001<code>NOW.plusSeconds()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param messageId \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
     * @param attempts \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>int</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQOutboxRecord</code> \u7ed3\u679c
     */
    private static MQOutboxRecord record(String messageId, int attempts) {
        return new MQOutboxRecord(messageId, "orders.created", "{}", Collections.emptyMap(), MQOutboxStatus.LEASED,
                NOW, "instance-a", NOW.plusSeconds(60), attempts, null, null);
    }

    /** \u9a8c\u8bc1<code>sent</code> \u7b49\u4e8e <code>Collections.singletonList("message-1")</code>\uff0c<code>store.leaseOwner</code> \u7b49\u4e8e <code>"instance-a"</code>\uff0c<code>observer.outboxOutcomes</code> \u7b49\u4e8e <code>Collections.singletonList("published")</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>store.rescheduled</code> \u7b49\u4e8e <code>Collections.singletonList("message-1")</code>\uff0c<code>observer.outboxOutcomes</code> \u7b49\u4e8e <code>Collections.singletonList("retry")</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>observer.outboxOutcomes</code> \u7b49\u4e8e <code>Collections.singletonList("dead")</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>observer.outboxOutcomes</code> \u7b49\u4e8e <code>Collections.singletonList("failed")</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>sent</code> \u7b49\u4e8e <code>Collections.singletonList("message-1")</code>\uff0c<code>store.rescheduled</code> \u7b49\u4e8e <code>Collections.emptyList()</code>\uff0c<code>observer.outboxOutcomes</code> \u7b49\u4e8e <code>Collections.singletonList("failed")</code>\u3002 */
    @Test
    void dispatch_shouldKeepLeaseWhenConfirmationThrowsAfterSend() {
        RecordingStore store = new RecordingStore(Arrays.asList(record("message-1", 1)));
        store.confirmationFailure = true;
        RecordingObserver observer = new RecordingObserver();
        List<String> sent = new ArrayList<>();
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store,
                message -> sent.add(message.messageId()), MQDeliveryPolicy.productionDefault(), observer);

        MQOutboxDispatchResult result = dispatcher.dispatch("instance-a", 10, NOW);

        assertEquals(Collections.singletonList("message-1"), sent);
        assertEquals(new MQOutboxDispatchResult(1, 0, 0, 0, 1), result);
        assertEquals(Collections.emptyList(), store.rescheduled);
        assertEquals(Collections.singletonList("failed"), observer.outboxOutcomes);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arrays.asList()</code>\u3001<code>record()</code>\u3001<code>MQDeliveryPolicy.productionDefault()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>dispatcher.dispatch("instance-a", 10, NOW)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>observer.outboxOutcomes</code> \u7b49\u4e8e <code>Collections.singletonList("failed")</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>dispatcher.dispatch("", 1, NOW)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>dispatcher.dispatch("instance-a", 0, NOW)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void dispatch_shouldRejectInvalidOwnerAndLimit() {
        RecordingStore store = new RecordingStore(Arrays.asList());
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store, message -> {
        }, MQDeliveryPolicy.productionDefault());

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("", 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("instance-a", 0, NOW));
    }

    /** \u5b9e\u73b0 <code>MQOutboxStore</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>append()</code>\u3001<code>claim()</code>\u3001<code>markPublished()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class RecordingStore implements MQOutboxStore {

        /** \u7528\u4e8e\u89c2\u5bdf\u5df2\u88ab\u6d3e\u53d1\u5668\u8ba4\u9886\u7684\u5f85\u53d1\u8bb0\u5f55\u3002 */
        private final List<MQOutboxRecord> claimed;
        /** \u6d4b\u8bd5\u53d1\u5e03\u5668\u5b9e\u9645\u63a5\u6536\u5230\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u3002 */
        private final List<String> published = new ArrayList<>();
        /** \u6d4b\u8bd5\u89c2\u5bdf\u5230\u5df2\u7ecf\u91cd\u65b0\u5b89\u6392\u7684\u53d1\u9001\u8bb0\u5f55\u3002 */
        private final List<String> rescheduled = new ArrayList<>();
        /** \u8ba4\u9886\u5f85\u6295\u9012\u8bb0\u5f55\u65f6\u4f7f\u7528\u7684\u6d4b\u8bd5\u4efb\u52a1\u6807\u8bc6\u3002 */
        private String leaseOwner;
        /** \u8bb0\u5f55\u6d4b\u8bd5\u4e2d\u7684\u53d1\u5e03\u7ed3\u679c\u662f\u5426\u5df2\u7ecf\u786e\u8ba4\u3002 */
        private boolean publishConfirmed = true;
        /** \u6a21\u62df\u4e3b\u9898\u53d1\u9001\u6210\u529f\u540e\u5199\u5165\u786e\u8ba4\u72b6\u6001\u65f6\u7684\u5931\u8d25\u3002 */
        private boolean confirmationFailure;
        /** \u6a21\u62df\u5c06\u4e8b\u4ef6\u91cd\u65b0\u5b89\u6392\u6295\u9012\u5931\u8d25\u7684\u5f02\u5e38\u3002 */
        private boolean rescheduleFailure;

        /**
         * \u8bbe\u7f6e <code>claimed</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>RecordingStore</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param claimed \u7528\u4e8e\u521d\u59cb\u5316 <code>RecordingStore</code> \u7684 <code>List&lt;MQOutboxRecord&gt;</code> \u503c
         */
        private RecordingStore(List<MQOutboxRecord> claimed) {
            this.claimed = claimed;
        }

        /** {@inheritDoc} */
        @Override
        public void append(MQOutboxRecord record) {
        }

        /** {@inheritDoc} */
        @Override
        public List<MQOutboxRecord> claim(String leaseOwner, Instant now, int limit, MQDeliveryPolicy policy) {
            this.leaseOwner = leaseOwner;
            return claimed;
        }

        /** {@inheritDoc} */
        @Override
        public boolean markPublished(String messageId, String leaseOwner, Instant publishedAt) {
            published.add(messageId);
            if (confirmationFailure) {
                throw new IllegalStateException("confirmation outcome unknown");
            }
            return publishConfirmed;
        }

        /** {@inheritDoc} */
        @Override
        public boolean reschedule(String messageId, String leaseOwner, Instant failedAt, String lastError,
                                  MQDeliveryPolicy policy) {
            if (rescheduleFailure) {
                throw new IllegalStateException("outbox unavailable");
            }
            rescheduled.add(messageId);
            return true;
        }

        /** {@inheritDoc} */
        @Override
        public boolean replay(String messageId, Instant availableAt) {
            return false;
        }
    }

    /** \u8bb0\u5f55\u6295\u9012\u72b6\u6001\u901a\u77e5\u4ee5\u9a8c\u8bc1\u53ef\u89c2\u6d4b\u6027\u548c\u5931\u8d25\u8def\u5f84\u3002 */
    private static final class RecordingObserver implements MQDeliveryObserver {

        /** \u8bb0\u5f55\u53d1\u4ef6\u7bb1\u89c2\u5bdf\u5668\u62a5\u544a\u7684\u5404\u6b21\u6295\u9012\u7ed3\u679c\u3002 */
        private final List<String> outboxOutcomes = new ArrayList<>();

        /** {@inheritDoc} */
        @Override
        public void onOutboxPublished(MQOutboxRecord record) {
            outboxOutcomes.add("published");
        }

        /** {@inheritDoc} */
        @Override
        public void onOutboxRetry(MQOutboxRecord record) {
            outboxOutcomes.add("retry");
        }

        /** {@inheritDoc} */
        @Override
        public void onOutboxDead(MQOutboxRecord record) {
            outboxOutcomes.add("dead");
        }

        /** {@inheritDoc} */
        @Override
        public void onOutboxFailed(MQOutboxRecord record) {
            outboxOutcomes.add("failed");
        }
    }
}
