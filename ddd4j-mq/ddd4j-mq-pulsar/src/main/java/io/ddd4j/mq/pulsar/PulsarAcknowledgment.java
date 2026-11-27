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
    /**
     * 记录已确认消息的状态或结果。
     */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 创建 Pulsar 消息确认句柄。
     *
     * @param consumer      Pulsar 消费者
     * @param message       待确认的消息
     * @param messageId     消息标识
     * @param correlationId 关联标识
     */
    public PulsarAcknowledgment(Consumer<?> consumer, Message<?> message,
                                String messageId, String correlationId) {
        this.consumer = consumer;
        this.message = message;
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.deliveryId = Objects.isNull(messageId) ? 0L : Math.abs((long) messageId.hashCode());
    }

    /**
     * 返回本次消息投递的标识。
     *
     * @return 当前投递的标记值
     */
    @Override
    public long deliveryTag() {
        return deliveryId;
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
     * <p>此实现调用 <code>consumer.isConnected()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(consumer) &amp;&amp; consumer.isConnected()</code>
     */
    @Override
    public boolean isOpen() {
        return Objects.nonNull(consumer) && consumer.isConnected();
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
        return BrokerType.PULSAR;
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

    /**
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>此实现调用 <code>nack(false, requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /**
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * @param multiple 是否批量否定
     * @param requeue  是否重新入队
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (!requeue) {
            throw new UnsupportedOperationException("Pulsar negative acknowledgment always redelivers");
        }
        runOnce(() -> ((Consumer) consumer).negativeAcknowledge(message));
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
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(type.cast(consumer))</code>、<code>Optional.of(type.cast(message))</code>
     */
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
     * 执行一次原生消息确认动作，代理调用失败时释放确认标记。
     *
     * @param op 待执行的代理确认或拒绝动作
     * @throws UnsupportedOperationException 当前消息的确认机会已被占用
     * @throws IllegalStateException         原生代理调用失败
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

    /**
     * 表示消息确认适配器执行的底层代理操作。
     */
    @FunctionalInterface
    private interface IoOperation {
        /**
         * 执行一次原生代理的消息确认操作。
         *
         * @throws Exception 原生确认操作执行失败
         */
        void run() throws Exception;
    }
}
