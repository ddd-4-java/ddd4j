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
package io.ddd4j.mq.nats;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.kit.lang.StrKit;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 NATS JetStream {@link Message} 的消息确认实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class NatsAcknowledgment implements Acknowledgment {

    /** \u5f53\u524d\u6d88\u8d39\u786e\u8ba4\u6240\u5173\u8054\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f\u3002 */
    private final Message message;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 NATS 确认对象。
     *
     * @param message JetStream 消息
     */
    public NatsAcknowledgment(Message message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return Objects.isNull(message.metaData()) ? 0L : message.metaData().consumerSequence();
    }

    /** {@inheritDoc} */
    @Override
    public String messageId() {
        return message.getSID();
    }

    /** {@inheritDoc} */
    @Override
    public String correlationId() {
        Headers headers = message.getHeaders();
        if (Objects.nonNull(headers)) {
            String correlationId = headers.getFirst(MQDeliveryHeaders.CORRELATION_ID);
            if (StrKit.isNotEmpty(correlationId)) {
                return correlationId;
            }
        }
        return message.getReplyTo();
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
        return BrokerType.NATS;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("NATS ignores multiple ack flag, acknowledging sequence={}", deliveryTag());
        }
        // 逻辑块：JetStream 确认成功消费
        message.ack();
        acknowledged.set(true);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("NATS ignores multiple nack flag, sequence={}", deliveryTag());
        }
        if (!requeue) {
            throw new UnsupportedOperationException(
                    "NATS JetStream nack without requeue is not supported; use ack or nak with requeue");
        }
        // 逻辑块：否定确认并重新投递
        message.nak();
        acknowledged.set(true);
    }

    /** {@inheritDoc} */
    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void recover(boolean requeue) {
        throw new UnsupportedOperationException("NATS does not support basicRecover semantics");
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (Message.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(message));
        }
        if (NatsAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回底层 NATS 消息。
     *
     * @return 底层 NATS {@link Message} 实例
     */
    public Message message() {
        return message;
    }

    /**
     * 防止重复确认导致 JetStream 状态异常。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new IllegalStateException("NATS message already acknowledged, sequence=" + deliveryTag());
        }
    }
}
