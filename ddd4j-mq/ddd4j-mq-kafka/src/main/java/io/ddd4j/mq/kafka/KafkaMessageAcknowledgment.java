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
package io.ddd4j.mq.kafka;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.message.MessageHeaders;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka manual acknowledgment mapping.
 *
 * <p>复用 {@link MessageHeaders} 公共常量（{@code ddd4j.message.id} / {@code ddd4j.correlation.id}），
 * 与 core Producer 写入的 header key 对齐。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class KafkaMessageAcknowledgment implements Acknowledgment {

    /**
     * Header 键：Kafka Consumer 实例（用于 unwrap）。
     */
    public static final String HEADER_KAFKA_CONSUMER = "ddd4j.kafka.consumer";
    /**
     * Header 键：Kafka ConsumerRecord 实例（用于 unwrap）。
     */
    public static final String HEADER_KAFKA_RECORD = "ddd4j.kafka.record";

    /** \u5f53\u524d\u5de5\u4f5c\u7ebf\u7a0b\u8f6e\u8be2\u3001\u6d88\u8d39\u6216\u63d0\u4ea4\u4f4d\u70b9\u65f6\u4f7f\u7528\u7684\u539f\u751f\u6d88\u8d39\u8005\u3002 */
    private final Consumer<?, ?> consumer;
    /** \u5f53\u524d\u786e\u8ba4\u52a8\u4f5c\u5173\u8054\u7684 Kafka \u539f\u751f\u8bb0\u5f55\u3002 */
    private final ConsumerRecord<?, ?> record;
    /** \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002 */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 创建 Kafka 消息确认句柄。
     *
     * @param consumer Kafka 消费者
     * @param record   待确认的 Kafka 记录
     */
    public KafkaMessageAcknowledgment(Consumer<?, ?> consumer, ConsumerRecord<?, ?> record) {
        this.consumer = Objects.requireNonNull(consumer, "Kafka Consumer is required");
        this.record = Objects.requireNonNull(record, "ConsumerRecord is required");
    }

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return record.offset();
    }

    /** {@inheritDoc} */
    @Override
    public String messageId() {
        String header = header(MessageHeaders.HEADER_MESSAGE_ID);
        if (Objects.isNull(header)) {
            header = header(MessageHeaders.LEGACY_HEADER_MESSAGE_ID);
        }
        return Objects.isNull(header) ? record.topic() + "-" + record.partition() + "-" + record.offset() : header;
    }

    /** {@inheritDoc} */
    @Override
    public String correlationId() {
        return header(MessageHeaders.HEADER_CORRELATION_ID);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOpen() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /** {@inheritDoc} */
    @Override
    public BrokerType brokerType() {
        return BrokerType.KAFKA;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void ack(boolean multiple) {
        if (!acknowledged.get()) {
            TopicPartition partition = new TopicPartition(record.topic(), record.partition());
            OffsetAndMetadata offset = new OffsetAndMetadata(record.offset() + 1);
            // 只有 broker 确认 offset 提交后才设置状态；提交失败仍须允许 seek 和重试。
            consumer.commitSync(Map.of(partition, offset));
            acknowledged.set(true);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (!requeue) {
            ack(multiple);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void recover(boolean requeue) {
        if (!requeue) {
            throw new UnsupportedOperationException(BrokerType.KAFKA + " recover(false) is not supported");
        }
        consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (Objects.isNull(nativeType)) {
            return Optional.empty();
        }
        if (nativeType.isInstance(consumer)) {
            return Optional.of(nativeType.cast(consumer));
        }
        if (nativeType.isInstance(record)) {
            return Optional.of(nativeType.cast(record));
        }
        return Optional.empty();
    }

    /**
     * \u8bfb\u53d6 Kafka \u8bb0\u5f55\u4e2d\u6307\u5b9a\u952e\u7684\u6d88\u606f\u5934\u3002
     * @param key \u5f85\u67e5\u8be2\u7684\u6d88\u606f\u5934\u6216\u5c5e\u6027\u540d\u79f0
     * @return \u89e3\u6790\u5f97\u5230\u7684\u6d88\u606f\u5934\u503c
     */
    private String header(String key) {
        Header header = record.headers().lastHeader(key);
        if (Objects.isNull(header) || Objects.isNull(header.value())) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
