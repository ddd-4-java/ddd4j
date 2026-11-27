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
package io.ddd4j.mq.rocketmq;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.message.Acknowledgment;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RocketMQ acknowledgment state adapter.
 *
 * <p>RocketMQ returns consume status from listener instead of exposing broker-level ack calls.
 * This adapter records whether ddd4j wants the message consumed successfully or retried.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RocketAcknowledgment implements Acknowledgment {

    /**
     * 原生 {@link MessageExt} 在 unwrap 时使用的类型标识头键。
     */
    public static final String HEADER_ROCKET_MESSAGE = "ddd4j.rocket.message";

    /** \u5f53\u524d\u6d88\u8d39\u786e\u8ba4\u6240\u5173\u8054\u7684\u539f\u751f\u4ee3\u7406\u6d88\u606f\u3002 */
    private final MessageExt message;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);
    /** \u5f53\u524d RocketMQ \u6d88\u8d39\u7ed3\u679c\u662f\u5426\u8981\u6c42\u7531\u4ee3\u7406\u518d\u6b21\u6295\u9012\u3002 */
    private volatile boolean reconsume;

    /**
     * 基于原生消息构造 ack 状态适配器。
     *
     * @param message 原生 RocketMQ 消息，不可为空
     */
    public RocketAcknowledgment(MessageExt message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return message.getQueueOffset();
    }

    /** {@inheritDoc} */
    @Override
    public String messageId() {
        return message.getMsgId();
    }

    /** {@inheritDoc} */
    @Override
    public String correlationId() {
        String correlationId = message.getUserProperty(MQDeliveryHeaders.CORRELATION_ID);
        return StrKit.isNotEmpty(correlationId)
                ? correlationId
                : message.getKeys();
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
        return BrokerType.ROCKET;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public void ack(boolean multiple) {
        complete(false);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        complete(requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void reject(boolean requeue) {
        complete(requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void recover(boolean requeue) {
        complete(requeue);
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (Objects.isNull(nativeType)) {
            return Optional.empty();
        }
        if (nativeType.isInstance(message)) {
            return Optional.of(nativeType.cast(message));
        }
        if (nativeType.isInstance(this)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 是否要求稍后重新消费（{@code nack}/{@code reject}/{@code recover} 且 {@code requeue=true} 时置位）。
     *
     * @return 需要重新消费返回 {@code true}
     */
    public boolean shouldReconsume() {
        return reconsume;
    }

    /**
     * \u539f\u5b50\u5360\u7528\u786e\u8ba4\u673a\u4f1a\u5e76\u4fdd\u5b58\u5f53\u524d\u6d88\u606f\u662f\u5426\u9700\u8981\u518d\u6b21\u6d88\u8d39\u3002
     * @param reconsume \u5f53\u524d\u6d88\u606f\u662f\u5426\u9700\u8981\u7531 RocketMQ \u518d\u6b21\u6295\u9012
     */
    private void complete(boolean reconsume) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("Message already acknowledged, msgId=" + message.getMsgId());
        }
        this.reconsume = reconsume;
    }
}
