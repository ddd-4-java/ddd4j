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
package io.ddd4j.mq.rabbitmq;

import com.rabbitmq.client.Channel;
import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RabbitMQ manual acknowledgment mapping.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RabbitAcknowledgment implements Acknowledgment {

    /** RabbitMQ 通道请求头名称。 */
    public static final String HEADER_RABBIT_CHANNEL = "ddd4j.rabbit.channel";
    /** RabbitMQ 投递标签请求头名称。 */
    public static final String HEADER_RABBIT_DELIVERY_TAG = "ddd4j.rabbit.deliveryTag";

    /** \u6267\u884c RabbitMQ \u6d88\u8d39\u786e\u8ba4\u7684\u4fe1\u9053\u3002 */
    private final Channel channel;
    /** \u7528\u4e8e\u5e95\u5c42\u4ee3\u7406\u786e\u8ba4\u6d88\u606f\u7684\u6295\u9012\u6807\u8bb0\u3002 */
    private final long deliveryTag;
    /** \u6d88\u606f\u6807\u8bc6\u3002 */
    private final String messageId;
    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 创建 RabbitMQ 消息确认句柄。
     *
     * @param channel RabbitMQ 通道
     * @param deliveryTag 投递标签
     * @param messageId 消息标识
     * @param correlationId 关联标识
     */
    public RabbitAcknowledgment(Channel channel, long deliveryTag, String messageId, String correlationId) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.deliveryTag = deliveryTag;
        this.messageId = messageId;
        this.correlationId = correlationId;
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
        return channel.isOpen();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /** {@inheritDoc} */
    @Override
    public BrokerType brokerType() {
        return BrokerType.RABBIT;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public void ack(boolean multiple) {
        runOnce(() -> channel.basicAck(deliveryTag, multiple));
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        runOnce(() -> channel.basicNack(deliveryTag, multiple, requeue));
    }

    /** {@inheritDoc} */
    @Override
    public void reject(boolean requeue) {
        runOnce(() -> channel.basicReject(deliveryTag, requeue));
    }

    /** {@inheritDoc} */
    @Override
    public void recover(boolean requeue) {
        runOnce(() -> channel.basicRecover(requeue));
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (Objects.isNull(nativeType)) {
            return Optional.empty();
        }
        if (nativeType.isInstance(channel)) {
            return Optional.of(nativeType.cast(channel));
        }
        if (nativeType.isInstance(this)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * \u6267\u884c\u4e00\u6b21\u539f\u751f\u6d88\u606f\u786e\u8ba4\u52a8\u4f5c\uff0c\u4ee3\u7406\u8c03\u7528\u5931\u8d25\u65f6\u91ca\u653e\u786e\u8ba4\u6807\u8bb0\u3002
     * @param operation \u5f85\u6267\u884c\u7684\u4ee3\u7406\u786e\u8ba4\u6216\u62d2\u7edd\u52a8\u4f5c
     * @throws UnsupportedOperationException \u5f53\u524d\u6d88\u606f\u7684\u786e\u8ba4\u673a\u4f1a\u5df2\u88ab\u5360\u7528
     * @throws IllegalStateException \u539f\u751f\u4ee3\u7406\u8c03\u7528\u5931\u8d25
     */
    private void runOnce(IoOperation operation) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("Message already acknowledged, deliveryTag=" + deliveryTag);
        }
        try {
            operation.run();
        } catch (IOException ex) {
            acknowledged.set(false);
            throw new IllegalStateException("RabbitMQ acknowledgment operation failed, deliveryTag=" + deliveryTag, ex);
        }
    }

    /** \u8868\u793a\u6d88\u606f\u786e\u8ba4\u9002\u914d\u5668\u6267\u884c\u7684\u5e95\u5c42\u4ee3\u7406\u64cd\u4f5c\u3002 */
    @FunctionalInterface
    private interface IoOperation {
        /**
         * \u6267\u884c\u4e00\u6b21\u539f\u751f\u4ee3\u7406\u7684\u6d88\u606f\u786e\u8ba4\u64cd\u4f5c\u3002
         * @throws IOException \u539f\u751f\u786e\u8ba4\u64cd\u4f5c\u6267\u884c\u5931\u8d25
         */
        void run() throws IOException;
    }
}
