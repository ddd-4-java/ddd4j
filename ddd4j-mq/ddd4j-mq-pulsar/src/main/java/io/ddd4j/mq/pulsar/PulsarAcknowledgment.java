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
package io.ddd4j.mq.pulsar;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Apache Pulsar 手动确认映射实现。
 *
 * <p>包装了 {@link Consumer} 和收到的 {@link Message}（Pulsar 消息，非 JMS 消息）。
 * Pulsar nack 语义：{@link Consumer#negativeAcknowledge(Message)} 重新投递消息（带可选延迟），
 * {@link Consumer#acknowledge(Message)} 确认消息。
 * 请求不重投的否定确认无法映射到 Pulsar 原生 nack，会显式失败并保留未确认状态。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PulsarAcknowledgment implements Acknowledgment {

    /**
     * Header 键：Pulsar 消费者
     */
    public static final String HEADER_PULSAR_CONSUMER = "ddd4j.pulsar.consumer";
    /**
     * Header 键：Pulsar 消息
     */
    public static final String HEADER_PULSAR_MESSAGE = "ddd4j.pulsar.message";
    /**
     * Header 键：Pulsar 消息 ID
     */
    public static final String HEADER_PULSAR_MESSAGE_ID = "ddd4j.pulsar.messageId";

    /**
     * Pulsar 消费者实例
     */
    private final Consumer<?> consumer;
    /**
     * Pulsar 消息实例
     */
    private final Message<?> message;
    /**
     * 消息 ID
     */
    private final String messageId;
    /**
     * 关联 ID
     */
    private final String correlationId;
    /**
     * 投递 ID
     */
    private final long deliveryId;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * \u521b\u5efa Pulsar \u6d88\u606f\u786e\u8ba4\u53e5\u67c4\u3002
     * @param consumer Pulsar \u6d88\u8d39\u8005
     * @param message \u5f85\u786e\u8ba4\u7684\u6d88\u606f
     * @param messageId \u6d88\u606f\u6807\u8bc6
     * @param correlationId \u5173\u8054\u6807\u8bc6
     */
    public PulsarAcknowledgment(Consumer<?> consumer, Message<?> message,
                                String messageId, String correlationId) {
        this.consumer = consumer;
        this.message = message;
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.deliveryId = Objects.isNull(messageId) ? 0L : Math.abs((long) messageId.hashCode());
    }

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return deliveryId;
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
        return Objects.nonNull(consumer) && consumer.isConnected();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /** {@inheritDoc} */
    @Override
    public BrokerType brokerType() {
        return BrokerType.PULSAR;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void ack(boolean multiple) {
        runOnce(() -> {
            if (multiple) {
                ((Consumer) consumer).acknowledgeCumulative(message.getMessageId());
            } else {
                ((Consumer) consumer).acknowledge(message);
            }
        });
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (!requeue) {
            throw new UnsupportedOperationException("Pulsar negative acknowledgment always redelivers");
        }
        runOnce(() -> ((Consumer) consumer).negativeAcknowledge(message));
    }

    /** {@inheritDoc} */
    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void recover(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (Objects.isNull(type)) {
            return Optional.empty();
        }
        if (type.isInstance(consumer)) {
            return Optional.of(type.cast(consumer));
        }
        if (type.isInstance(message)) {
            return Optional.of(type.cast(message));
        }
        if (type.isInstance(messageId())) {
            return Optional.of(type.cast(messageId()));
        }
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    /**
     * \u6267\u884c\u4e00\u6b21\u539f\u751f\u6d88\u606f\u786e\u8ba4\u52a8\u4f5c\uff0c\u4ee3\u7406\u8c03\u7528\u5931\u8d25\u65f6\u91ca\u653e\u786e\u8ba4\u6807\u8bb0\u3002
     * @param op \u5f85\u6267\u884c\u7684\u4ee3\u7406\u786e\u8ba4\u6216\u62d2\u7edd\u52a8\u4f5c
     * @throws UnsupportedOperationException \u5f53\u524d\u6d88\u606f\u7684\u786e\u8ba4\u673a\u4f1a\u5df2\u88ab\u5360\u7528
     * @throws IllegalStateException \u539f\u751f\u4ee3\u7406\u8c03\u7528\u5931\u8d25
     */
    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException(
                    "Pulsar message already acknowledged, id=" + messageId);
        }
        try {
            op.run();
        } catch (Exception ex) {
            acknowledged.set(false);
            throw new IllegalStateException("Pulsar ack failed, id=" + messageId, ex);
        }
    }

    /** \u8868\u793a\u6d88\u606f\u786e\u8ba4\u9002\u914d\u5668\u6267\u884c\u7684\u5e95\u5c42\u4ee3\u7406\u64cd\u4f5c\u3002 */
    @FunctionalInterface
    private interface IoOperation {
        /**
         * \u6267\u884c\u4e00\u6b21\u539f\u751f\u4ee3\u7406\u7684\u6d88\u606f\u786e\u8ba4\u64cd\u4f5c\u3002
         * @throws Exception \u539f\u751f\u786e\u8ba4\u64cd\u4f5c\u6267\u884c\u5931\u8d25
         */
        void run() throws Exception;
    }
}
