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
package io.ddd4j.mq.sqs;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.message.Acknowledgment;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AWS SQS 手动确认映射实现。
 *
 * <p>SQS 没有原生 ack 语义：ack 等价于 {@code deleteMessage(receiptHandle)}，nack(requeue=true)
 * 等价于 {@code changeMessageVisibility(receiptHandle, 0)}（让消息立即可被另一消费者接收）。
 * 配置禁用重投时，请求重投会显式失败，不会删除消息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SqsAcknowledgment implements Acknowledgment {

    /**
     * Header 键：SQS 客户端
     */
    public static final String HEADER_SQS_CLIENT = "ddd4j.sqs.client";
    /**
     * Header 键：SQS 消息体
     */
    public static final String HEADER_SQS_MESSAGE = "ddd4j.sqs.message";
    /**
     * Header 键：SQS 队列 URL
     */
    public static final String HEADER_SQS_QUEUE_URL = "ddd4j.sqs.queueUrl";

    /**
     * AWS SQS 客户端实例
     */
    private final SqsClient client;
    /**
     * SQS 消息实例
     */
    private final Message message;
    /**
     * 队列 URL
     */
    private final String queueUrl;
    /**
     * 消息 ID
     */
    private final String messageId;
    /** \u7528\u4e8e\u5173\u8054\u540c\u4e00\u6b21\u64cd\u4f5c\u4ea7\u751f\u7684\u4e8b\u4ef6\u6216\u6d88\u606f\u7684\u6807\u8bc6\u3002 */
    private final String correlationId;
    /**
     * 消息回执句柄
     */
    private final String receiptHandle;
    /**
     * 确认状态标记
     */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);
    /**
     * nack 时是否允许重新入队
     */
    private final boolean requeueOnNack;

    /**
     * 构造 SQS 消息确认实例。
     *
     * @param client        AWS SQS 客户端
     * @param message       SQS 消息
     * @param queueUrl      队列 URL
     * @param requeueOnNack nack 时是否允许重新入队
     */
    public SqsAcknowledgment(SqsClient client, Message message, String queueUrl, boolean requeueOnNack) {
        this.client = client;
        this.message = message;
        this.queueUrl = queueUrl;
        String stableId = SqsMQClient.messageId(message);
        this.messageId = Objects.nonNull(stableId) ? stableId : message.messageId();
        this.correlationId = SqsMQClient.attr(message, MQDeliveryHeaders.CORRELATION_ID);
        this.receiptHandle = message.receiptHandle();
        this.requeueOnNack = requeueOnNack;
    }

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return Objects.isNull(receiptHandle) ? 0L : (long) receiptHandle.hashCode();
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
        return Objects.nonNull(client);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /** {@inheritDoc} */
    @Override
    public BrokerType brokerType() {
        return BrokerType.SQS;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public void ack(boolean multiple) {
        runOnce(() -> client.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl).receiptHandle(receiptHandle).build()));
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (requeue && !requeueOnNack) {
            throw new UnsupportedOperationException("SQS requeue on nack is disabled");
        }
        if (requeue) {
            runOnce(() -> client.changeMessageVisibility(b -> b
                    .queueUrl(queueUrl).receiptHandle(receiptHandle).visibilityTimeout(0)));
        } else {
            // 不重投：仍走 deleteMessage
            runOnce(() -> client.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl).receiptHandle(receiptHandle).build()));
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
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (Objects.isNull(type)) {
            return Optional.empty();
        }
        if (type.isInstance(client)) {
            return Optional.of(type.cast(client));
        }
        if (type.isInstance(message)) {
            return Optional.of(type.cast(message));
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
            throw new UnsupportedOperationException("SQS message already ack'd, id=" + messageId);
        }
        try {
            op.run();
        } catch (Exception ex) {
            acknowledged.set(false);
            throw new IllegalStateException("SQS ack operation failed, id=" + messageId, ex);
        }
    }

    /** \u8868\u793a\u6d88\u606f\u786e\u8ba4\u9002\u914d\u5668\u6267\u884c\u7684\u5e95\u5c42\u4ee3\u7406\u64cd\u4f5c\u3002 */
    @FunctionalInterface
    private interface IoOperation {
        /** \u6267\u884c\u4e00\u6b21\u539f\u751f\u4ee3\u7406\u7684\u6d88\u606f\u786e\u8ba4\u64cd\u4f5c\u3002 */
        void run();
    }
}
