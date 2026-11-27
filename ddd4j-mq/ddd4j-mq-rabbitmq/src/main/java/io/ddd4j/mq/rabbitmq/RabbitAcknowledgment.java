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

    /**
     * RabbitMQ 通道请求头名称。
     */
    public static final String HEADER_RABBIT_CHANNEL = "ddd4j.rabbit.channel";
    /**
     * RabbitMQ 投递标签请求头名称。
     */
    public static final String HEADER_RABBIT_DELIVERY_TAG = "ddd4j.rabbit.deliveryTag";

    /**
     * 执行 RabbitMQ 消费确认的信道。
     */
    private final Channel channel;
    /**
     * 用于底层代理确认消息的投递标记。
     */
    private final long deliveryTag;
    /**
     * 消息标识。
     */
    private final String messageId;
    /**
     * 用于关联同一次操作产生的事件或消息的标识。
     */
    private final String correlationId;
    /**
     * 记录已确认消息的状态或结果。
     */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 创建 RabbitMQ 消息确认句柄。
     *
     * @param channel       RabbitMQ 通道
     * @param deliveryTag   投递标签
     * @param messageId     消息标识
     * @param correlationId 关联标识
     */
    public RabbitAcknowledgment(Channel channel, long deliveryTag, String messageId, String correlationId) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.deliveryTag = deliveryTag;
        this.messageId = messageId;
        this.correlationId = correlationId;
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
     * <p>RabbitMQ 实现调用 <code>channel.isOpen()</code>。</p>
     *
     * @return 实际返回<code>channel.isOpen()</code>
     */
    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * 判断当前投递是否已完成确认处理。
     *
     * <p>RabbitMQ 实现调用 <code>acknowledged.get()</code>。</p>
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
        return BrokerType.RABBIT;
    }

    /**
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>RabbitMQ 实现调用 <code>ack(false)</code>。</p>
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
        runOnce(() -> channel.basicAck(deliveryTag, multiple));
    }

    /**
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>RabbitMQ 实现调用 <code>nack(false, requeue)</code>。</p>
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
     * @param multiple 是否批量否定
     * @param requeue  是否重新入队
     */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        runOnce(() -> channel.basicNack(deliveryTag, multiple, requeue));
    }

    /**
     * 拒绝当前投递，并按约定处理是否重新入队。
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void reject(boolean requeue) {
        runOnce(() -> channel.basicReject(deliveryTag, requeue));
    }

    /**
     * 请求底层消息会话恢复未处理的投递。
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void recover(boolean requeue) {
        runOnce(() -> channel.basicRecover(requeue));
    }

    /**
     * 取出适配器持有的底层投递或客户端对象。
     *
     * <p>RabbitMQ 实现调用 <code>Optional.empty()</code>。</p>
     *
     * @param <T>        类型参数
     * @param nativeType 原生类型
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(nativeType.cast(channel))</code>、<code>Optional.of(nativeType.cast(this))</code>
     */
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
     * 执行一次原生消息确认动作，代理调用失败时释放确认标记。
     *
     * @param operation 待执行的代理确认或拒绝动作
     * @throws UnsupportedOperationException 当前消息的确认机会已被占用
     * @throws IllegalStateException         原生代理调用失败
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

    /**
     * 表示消息确认适配器执行的底层代理操作。
     */
    @FunctionalInterface
    private interface IoOperation {
        /**
         * 执行一次原生代理的消息确认操作。
         *
         * @throws IOException 原生确认操作执行失败
         */
        void run() throws IOException;
    }
}
