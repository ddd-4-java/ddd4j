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

    /**
     * 当前确认动作关联的 Disruptor 事件。
     */
    private final DisruptorEvent event;
    /**
     * 向 Disruptor 工作线程转交消息的环形缓冲区。
     */
    private final RingBuffer<DisruptorEvent> ringBuffer;
    /**
     * 用于底层代理确认消息的投递标记。
     */
    private final long deliveryTag;
    /**
     * 记录已确认消息的状态或结果。
     */
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

    /**
     * 返回本次消息投递的标识。
     *
     * @return 当前投递的标记值
     */
    @Override
    public long deliveryTag() {
        return deliveryTag;
    }

    /**
     * 返回收到的消息标识。
     *
     * <p>此实现调用 <code>event.getMessageId()</code>。</p>
     *
     * @return 当前消息的标识
     */
    @Override
    public String messageId() {
        return event.getMessageId();
    }

    /**
     * 返回消息所属链路的关联标识。
     *
     * <p>此实现调用 <code>event.getMessageId()</code>。</p>
     *
     * @return 当前消息关联的请求或事件标识
     */
    @Override
    public String correlationId() {
        return Objects.nonNull(event.getMessageId()) ? event.getMessageId() : null;
    }

    /**
     * 检查当前投递的确认对象是否仍可用。
     *
     * @return 实际返回<code>Objects.nonNull(ringBuffer)</code>，作为本次判断结果
     */
    @Override
    public boolean isOpen() {
        return Objects.nonNull(ringBuffer);
    }

    /**
     * 判断当前投递是否已完成确认处理。
     *
     * <p>此实现调用 <code>acknowledged.get()</code>。</p>
     *
     * @return 此投递已经进入确认终态时为真
     */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /**
     * 返回当前投递所使用的消息中间件类型。
     *
     * @return 客户端或投递协议对应的 broker 枚举值
     */
    @Override
    public BrokerType brokerType() {
        return BrokerType.DISRUPTOR;
    }

    /**
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>此实现调用 <code>ack(false)</code>。</p>
     */
    @Override
    public void ack() {
        ack(false);
    }

    /**
     * 通过底层消息会话更新当前投递的确认状态。
     *
     * @param multiple 是否批量确认至当前 tag
     */
    @Override
    public void ack(boolean multiple) {
        acknowledged.set(true);
    }

    /**
     * 通过底层消息会话更新当前投递的确认状态。
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void nack(boolean requeue) {
        if (requeue && Objects.nonNull(ringBuffer) && !acknowledged.get()) {
            republish();
        }
        acknowledged.set(true);
    }

    /**
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>此实现调用 <code>nack(requeue)</code>。</p>
     *
     * @param multiple 是否批量否定
     * @param requeue  是否重新入队
     */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        nack(requeue);
    }

    /**
     * 拒绝当前投递，并按约定处理是否重新入队。
     *
     * <p>此实现调用 <code>nack(requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    /**
     * 请求底层消息会话恢复未处理的投递。
     *
     * <p>满足 <code>!requeue</code> 时抛出 <code>UnsupportedOperationException</code>。 此实现调用 <code>nack(true)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void recover(boolean requeue) {
        if (!requeue) {
            throw new UnsupportedOperationException("Disruptor does not support recover(false)");
        }
        nack(true);
    }

    /**
     * 取出适配器持有的底层投递或客户端对象。
     *
     * <p>此实现调用 <code>nativeType.isInstance(event)</code>。</p>
     *
     * @param <T>        类型参数
     * @param nativeType 原生类型
     * @return 根据执行路径返回<code>Optional.of(nativeType.cast(event))</code>、<code>Optional.empty()</code>
     */
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
