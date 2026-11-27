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
    /**
     * 用于关联同一次操作产生的事件或消息的标识。
     */
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

    /**
     * 返回本次消息投递的标识。
     *
     * <p>此实现调用 <code>receiptHandle.hashCode()</code>。</p>
     *
     * @return 当前投递的标记值
     */
    @Override
    public long deliveryTag() {
        return Objects.isNull(receiptHandle) ? 0L : (long) receiptHandle.hashCode();
    }

    /**
     * 返回收到的消息标识。
     *
     * @return 当前消息的标识
     */
    @Override
    public String messageId() {
        return messageId;
    }

    /**
     * 返回消息所属链路的关联标识。
     *
     * @return 当前消息关联的请求或事件标识
     */
    @Override
    public String correlationId() {
        return correlationId;
    }

    /**
     * 检查当前投递的确认对象是否仍可用。
     *
     * @return 实际返回<code>Objects.nonNull(client)</code>，作为本次判断结果
     */
    @Override
    public boolean isOpen() {
        return Objects.nonNull(client);
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
        return BrokerType.SQS;
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
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * @param multiple 是否批量确认至当前 tag
     */
    @Override
    public void ack(boolean multiple) {
        runOnce(() -> client.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl).receiptHandle(receiptHandle).build()));
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
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>满足 <code>requeue &amp;&amp; !requeueOnNack</code> 时抛出 <code>UnsupportedOperationException</code>。</p>
     *
     * @param multiple 是否批量否定
     * @param requeue  是否重新入队
     */
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

    /**
     * 拒绝当前投递，并按约定处理是否重新入队。
     *
     * <p>此实现调用 <code>nack(false, requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    /**
     * 请求底层消息会话恢复未处理的投递。
     *
     * <p>此实现调用 <code>nack(false, requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void recover(boolean requeue) {
        nack(false, requeue);
    }

    /**
     * 取出适配器持有的底层投递或客户端对象。
     *
     * <p>此实现调用 <code>Optional.empty()</code>。</p>
     *
     * @param <T>  类型参数
     * @param type 当前操作用于选择实现或转换结果的类型
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(type.cast(client))</code>、<code>Optional.of(type.cast(message))</code>
     */
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
     * 执行一次原生消息确认动作，代理调用失败时释放确认标记。
     *
     * @param op 待执行的代理确认或拒绝动作
     * @throws UnsupportedOperationException 当前消息的确认机会已被占用
     * @throws IllegalStateException         原生代理调用失败
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

    /**
     * 表示消息确认适配器执行的底层代理操作。
     */
    @FunctionalInterface
    private interface IoOperation {
        /**
         * 执行一次原生代理的消息确认操作。
         */
        void run();
    }
}
