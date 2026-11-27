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

/**
 * 验证 <code>MQOutboxDispatcher</code> 在<code>dispatch</code>等操作中的行为契约。
 */
class MQOutboxDispatcherTest {

    /**
     * 用来固定事件或位点时间的测试时间点。
     */
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    /**
     * 调用 <code>Collections.emptyMap()</code>、<code>NOW.plusSeconds()</code> 执行当前夹具操作。
     *
     * @param messageId 用于构造本次返回结果的 <code>String</code> 输入
     * @param attempts  用于构造本次返回结果的 <code>int</code> 输入
     * @return 提供给后续契约断言的 <code>MQOutboxRecord</code> 结果
     */
    private static MQOutboxRecord record(String messageId, int attempts) {
        return new MQOutboxRecord(messageId, "orders.created", "{}", Collections.emptyMap(), MQOutboxStatus.LEASED,
                NOW, "instance-a", NOW.plusSeconds(60), attempts, null, null);
    }

    /**
     * 验证<code>sent</code> 等于 <code>Collections.singletonList("message-1")</code>，<code>store.leaseOwner</code> 等于 <code>"instance-a"</code>，<code>observer.outboxOutcomes</code> 等于 <code>Collections.singletonList("published")</code>。
     */
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

    /**
     * 验证<code>store.rescheduled</code> 等于 <code>Collections.singletonList("message-1")</code>，<code>observer.outboxOutcomes</code> 等于 <code>Collections.singletonList("retry")</code>。
     */
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

    /**
     * 验证<code>observer.outboxOutcomes</code> 等于 <code>Collections.singletonList("dead")</code>。
     */
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

    /**
     * 验证<code>observer.outboxOutcomes</code> 等于 <code>Collections.singletonList("failed")</code>。
     */
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

    /**
     * 验证<code>sent</code> 等于 <code>Collections.singletonList("message-1")</code>，<code>store.rescheduled</code> 等于 <code>Collections.emptyList()</code>，<code>observer.outboxOutcomes</code> 等于 <code>Collections.singletonList("failed")</code>。
     */
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

    /**
     * 在测试框架中执行 <code>Arrays.asList()</code>、<code>record()</code>、<code>MQDeliveryPolicy.productionDefault()</code>，验证该场景。
     */
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

    /**
     * 验证<code>dispatcher.dispatch("instance-a", 10, NOW)</code> 抛出 <code>IllegalStateException.class</code>，<code>observer.outboxOutcomes</code> 等于 <code>Collections.singletonList("failed")</code>。
     */
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

    /**
     * 验证<code>dispatcher.dispatch("", 1, NOW)</code> 抛出 <code>IllegalArgumentException.class</code>，<code>dispatcher.dispatch("instance-a", 0, NOW)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void dispatch_shouldRejectInvalidOwnerAndLimit() {
        RecordingStore store = new RecordingStore(Arrays.asList());
        MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store, message -> {
        }, MQDeliveryPolicy.productionDefault());

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("", 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("instance-a", 0, NOW));
    }

    /**
     * 实现 <code>MQOutboxStore</code>，供测试调用 <code>append()</code>、<code>claim()</code>、<code>markPublished()</code> 并观察结果。
     */
    private static final class RecordingStore implements MQOutboxStore {

        /**
         * 用于观察已被派发器认领的待发记录。
         */
        private final List<MQOutboxRecord> claimed;
        /**
         * 测试发布器实际接收到的事件或消息。
         */
        private final List<String> published = new ArrayList<>();
        /**
         * 测试观察到已经重新安排的发送记录。
         */
        private final List<String> rescheduled = new ArrayList<>();
        /**
         * 认领待投递记录时使用的测试任务标识。
         */
        private String leaseOwner;
        /**
         * 记录测试中的发布结果是否已经确认。
         */
        private boolean publishConfirmed = true;
        /**
         * 模拟主题发送成功后写入确认状态时的失败。
         */
        private boolean confirmationFailure;
        /**
         * 模拟将事件重新安排投递失败的异常。
         */
        private boolean rescheduleFailure;

        /**
         * 设置 <code>claimed</code>，用于构造 <code>RecordingStore</code> 测试实例。
         *
         * @param claimed 用于初始化 <code>RecordingStore</code> 的 <code>List&lt;MQOutboxRecord&gt;</code> 值
         */
        private RecordingStore(List<MQOutboxRecord> claimed) {
            this.claimed = claimed;
        }

        /**
         * 提供测试替身回调，调用时保持夹具状态。
         *
         * @param record 待发布消息
         */
        @Override
        public void append(MQOutboxRecord record) {
        }

        /**
         * 原子领取当前可投递的消息，并写入发布实例的短期租约。
         *
         * <p>此实现更新<code>this.leaseOwner = leaseOwner</code>。</p>
         *
         * @param leaseOwner 当前发布实例标识
         * @param now        当前时间
         * @param limit      最多领取数量
         * @param policy     投递策略
         * @return 实际返回<code>claimed</code>，供调用方取得当前值
         */
        @Override
        public List<MQOutboxRecord> claim(String leaseOwner, Instant now, int limit, MQDeliveryPolicy policy) {
            this.leaseOwner = leaseOwner;
            return claimed;
        }

        /**
         * 将指定发件箱记录标记为已发布。
         *
         * <p>测试夹具会调用 <code>published.add(messageId)</code>。</p>
         *
         * @param messageId   消息标识
         * @param leaseOwner  租约持有者
         * @param publishedAt 发布确认时间
         * @return 实际返回<code>publishConfirmed</code>
         */
        @Override
        public boolean markPublished(String messageId, String leaseOwner, Instant publishedAt) {
            published.add(messageId);
            if (confirmationFailure) {
                throw new IllegalStateException("confirmation outcome unknown");
            }
            return publishConfirmed;
        }

        /**
         * 为失败的发件箱记录安排下一次发布。
         *
         * <p>满足 <code>rescheduleFailure</code> 时抛出 <code>IllegalStateException</code>。 测试夹具会调用 <code>rescheduled.add(messageId)</code>。</p>
         *
         * @param messageId  消息标识
         * @param leaseOwner 租约持有者
         * @param failedAt   失败时间
         * @param lastError  可诊断失败原因
         * @param policy     投递策略
         * @return 此实现返回<code>true</code>
         */
        @Override
        public boolean reschedule(String messageId, String leaseOwner, Instant failedAt, String lastError,
                                  MQDeliveryPolicy policy) {
            if (rescheduleFailure) {
                throw new IllegalStateException("outbox unavailable");
            }
            rescheduled.add(messageId);
            return true;
        }

        /**
         * 将死信显式重放为待投递状态。
         *
         * @param messageId   消息标识
         * @param availableAt 重放可投递时间
         * @return 此实现返回<code>false</code>
         */
        @Override
        public boolean replay(String messageId, Instant availableAt) {
            return false;
        }
    }

    /**
     * 记录投递状态通知以验证可观测性和失败路径。
     */
    private static final class RecordingObserver implements MQDeliveryObserver {

        /**
         * 记录发件箱观察器报告的各次投递结果。
         */
        private final List<String> outboxOutcomes = new ArrayList<>();

        /**
         * Outbox 消息已由持有租约的实例确认发布。
         *
         * <p>测试夹具会调用 <code>outboxOutcomes.add("published")</code>。</p>
         *
         * @param record 已发布记录
         */
        @Override
        public void onOutboxPublished(MQOutboxRecord record) {
            outboxOutcomes.add("published");
        }

        /**
         * Outbox 消息已安排后续重试。
         *
         * <p>测试夹具会调用 <code>outboxOutcomes.add("retry")</code>。</p>
         *
         * @param record 投递失败记录
         */
        @Override
        public void onOutboxRetry(MQOutboxRecord record) {
            outboxOutcomes.add("retry");
        }

        /**
         * Outbox 消息已超过重试上限并进入死信状态。
         *
         * <p>测试夹具会调用 <code>outboxOutcomes.add("dead")</code>。</p>
         *
         * @param record 死信记录
         */
        @Override
        public void onOutboxDead(MQOutboxRecord record) {
            outboxOutcomes.add("dead");
        }

        /**
         * Outbox 发送或状态确认失败，最终状态尚未确定。
         *
         * <p>测试夹具会调用 <code>outboxOutcomes.add("failed")</code>。</p>
         *
         * @param record 投递状态不确定的记录
         */
        @Override
        public void onOutboxFailed(MQOutboxRecord record) {
            outboxOutcomes.add("failed");
        }
    }
}
