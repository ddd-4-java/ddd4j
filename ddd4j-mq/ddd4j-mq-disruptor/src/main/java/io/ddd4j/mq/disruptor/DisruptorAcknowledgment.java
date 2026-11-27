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
package io.ddd4j.mq.disruptor;

import com.lmax.disruptor.RingBuffer;
import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Disruptor 本地消息确认实现：ack 为消费完成；requeue 重新发布到 RingBuffer。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DisruptorAcknowledgment implements Acknowledgment {

    /** \u5f53\u524d\u786e\u8ba4\u52a8\u4f5c\u5173\u8054\u7684 Disruptor \u4e8b\u4ef6\u3002 */
    private final DisruptorEvent event;
    /** \u5411 Disruptor \u5de5\u4f5c\u7ebf\u7a0b\u8f6c\u4ea4\u6d88\u606f\u7684\u73af\u5f62\u7f13\u51b2\u533a\u3002 */
    private final RingBuffer<DisruptorEvent> ringBuffer;
    /** \u7528\u4e8e\u5e95\u5c42\u4ee3\u7406\u786e\u8ba4\u6d88\u606f\u7684\u6295\u9012\u6807\u8bb0\u3002 */
    private final long deliveryTag;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * @param event       当前 ddd4j Disruptor 事件
     * @param ringBuffer  RingBuffer（requeue 用）
     * @param deliveryTag 投递序号
     */
    public DisruptorAcknowledgment(
            DisruptorEvent event,
            RingBuffer<DisruptorEvent> ringBuffer,
            long deliveryTag) {
        this.event = event;
        this.ringBuffer = ringBuffer;
        this.deliveryTag = deliveryTag;
    }

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return deliveryTag;
    }

    /** {@inheritDoc} */
    @Override
    public String messageId() {
        return event.getMessageId();
    }

    /** {@inheritDoc} */
    @Override
    public String correlationId() {
        return Objects.nonNull(event.getMessageId()) ? event.getMessageId() : null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOpen() {
        return Objects.nonNull(ringBuffer);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /** {@inheritDoc} */
    @Override
    public BrokerType brokerType() {
        return BrokerType.DISRUPTOR;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public void ack(boolean multiple) {
        acknowledged.set(true);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        if (requeue && Objects.nonNull(ringBuffer) && !acknowledged.get()) {
            republish();
        }
        acknowledged.set(true);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        nack(requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void recover(boolean requeue) {
        if (!requeue) {
            throw new UnsupportedOperationException("Disruptor does not support recover(false)");
        }
        nack(true);
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (nativeType.isInstance(event)) {
            return Optional.of(nativeType.cast(event));
        }
        return Optional.empty();
    }

    /**
     * 将当前事件重新发布到 RingBuffer（本地 requeue）。
     */
    private void republish() {
        DisruptorEvent src = this.event;
        ringBuffer.publishEvent((slot, sequence) -> {
            slot.setTopic(src.getTopic());
            slot.setTag(src.getTag());
            slot.setNamespace(src.getNamespace());
            slot.setMessageId(src.getMessageId());
            slot.setPayload(src.getPayload());
            slot.setSequence(sequence);
        });
    }
}
