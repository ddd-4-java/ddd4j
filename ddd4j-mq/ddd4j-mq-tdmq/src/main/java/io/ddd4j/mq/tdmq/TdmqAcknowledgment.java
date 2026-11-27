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
package io.ddd4j.mq.tdmq;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 腾讯云 TDMQ 消息确认实现。
 *
 * <p>TDMQ 是腾讯云基于 Pulsar 的托管服务。{@link #ackCallback} 暴露由业务侧 TDMQ 客户端传入的 ack 回调，
 * 由 ddd4j-MQ 的 consume 流程根据执行结果决定 ack/nack/requeue。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class TdmqAcknowledgment implements Acknowledgment {

    /** \u6d88\u606f\u6807\u8bc6\u3002 */
    private final String messageId;
    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /** \u7528\u4e8e\u5e95\u5c42\u4ee3\u7406\u786e\u8ba4\u6d88\u606f\u7684\u6295\u9012\u6807\u8bb0\u3002 */
    private final long deliveryTag;
    /** \u539f\u751f\u4ee3\u7406\u66b4\u9732\u7684\u6295\u9012\u786e\u8ba4\u56de\u8c03\u3002 */
    private final java.util.function.Consumer<Boolean> ackCallback;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * \u521b\u5efa TDMQ \u6d88\u606f\u786e\u8ba4\u53e5\u67c4\u3002
     * @param messageId \u6d88\u606f\u6807\u8bc6
     * @param correlationId \u5173\u8054\u6807\u8bc6
     * @param deliveryTag \u6295\u9012\u6807\u7b7e
     * @param ackCallback \u786e\u8ba4\u7ed3\u679c\u56de\u8c03
     */
    public TdmqAcknowledgment(String messageId,
                              String correlationId,
                              long deliveryTag,
                              java.util.function.Consumer<Boolean> ackCallback) {
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.deliveryTag = deliveryTag;
        this.ackCallback = Objects.requireNonNull(ackCallback, "ackCallback");
    }

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return deliveryTag;
    }

    /** {@inheritDoc} */
    @Override
    public String messageId() {
        return messageId;
    }

    /** {@inheritDoc} */
    @Override
    public String correlationId() {
        return correlationId;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOpen() {
        return !acknowledged.get();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /** {@inheritDoc} */
    @Override
    public BrokerType brokerType() {
        return BrokerType.TDMQ;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void ack(boolean multiple) {
        ensureNotAcknowledged();
        ackCallback.accept(true);
        acknowledged.set(true);
        log.debug("TDMQ ack messageId={}, multiple={}", messageId, multiple);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void nack(boolean multiple, boolean requeue) {
        ensureNotAcknowledged();
        ackCallback.accept(requeue);
        acknowledged.set(true);
        log.debug("TDMQ nack messageId={}, requeue={}, multiple={}", messageId, requeue, multiple);
    }

    /** {@inheritDoc} */
    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void recover(boolean requeue) {
        nack(requeue);
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (nativeType.isInstance(this)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /** \u62d2\u7edd\u91cd\u590d\u786e\u8ba4\u540c\u4e00\u6761 TDMQ \u6295\u9012\u6d88\u606f\u3002 */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new IllegalStateException("TDMQ message already acknowledged, messageId=" + messageId);
        }
    }
}
