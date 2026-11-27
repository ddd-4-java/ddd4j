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

    /**
     * 返回本次消息投递的标识。
     *
     * <p>此实现调用 <code>message.metaData()</code>。</p>
     *
     * @return 当前投递的标记值
     */
    @Override
    public long deliveryTag() {
        return Objects.isNull(message.metaData()) ? 0L : message.metaData().consumerSequence();
    }

    /**
     * 返回收到的消息标识。
     *
     * <p>此实现调用 <code>message.getSID()</code>。</p>
     *
     * @return 当前消息的标识
     */
    @Override
    public String messageId() {
        return message.getSID();
    }

    /**
     * 返回消息所属链路的关联标识。
     *
     * <p>此实现调用 <code>message.getHeaders()</code>。</p>
     *
     * @return 当前消息关联的请求或事件标识
     */
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

    /**
     * 检查当前投递的确认对象是否仍可用。
     *
     * <p>此实现调用 <code>acknowledged.get()</code>。</p>
     *
     * @return 实际返回<code>!acknowledged.get()</code>
     */
    @Override
    public boolean isOpen() {
        return !acknowledged.get();
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
        return BrokerType.NATS;
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
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("NATS ignores multiple ack flag, acknowledging sequence={}", deliveryTag());
        }
        // 逻辑块：JetStream 确认成功消费
        message.ack();
        acknowledged.set(true);
    }

    /**
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>此实现调用 <code>nack(false, requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /**
     * 通过底层消息会话更新当前投递的确认状态。
     *
     * @param multiple 是否批量否定
     * @param requeue 是否重新入队
     */
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
     * 明确拒绝当前实现不支持的消息投递操作。
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void recover(boolean requeue) {
        throw new UnsupportedOperationException("NATS does not support basicRecover semantics");
    }

    /**
     * 取出适配器持有的底层投递或客户端对象。
     *
     * <p>此实现调用 <code>Message.class.isAssignableFrom(nativeType)</code>。</p>
     *
     * @param <T> 类型参数
     * @param nativeType 原生类型
     * @return 根据执行路径返回<code>Optional.of(nativeType.cast(message))</code>、<code>Optional.of(nativeType.cast(this))</code>、<code>Optional.empty()</code>
     */
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
     * @return 底层 NATS 原生消息
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
