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
package io.ddd4j.mq.activemq;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ActiveMQ Artemis (JMS) 手动确认映射实现。
 *
 * <p>包装了 {@link Session} 和 JMS 消息；{@link #ack()} / {@link #nack(boolean)} 方法映射到
 * {@code Session.recover()} 和 JMS {@code Message.acknowledge()} 操作。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ActiveMQAcknowledgment implements Acknowledgment {

    /**
     * Header 键：ActiveMQ JMS Session
     */
    public static final String HEADER_AMQ_SESSION = "ddd4j.activemq.session";
    /**
     * Header 键：ActiveMQ JMS Message
     */
    public static final String HEADER_AMQ_MESSAGE = "ddd4j.activemq.message";
    /**
     * Header 键：ActiveMQ 投递 ID
     */
    public static final String HEADER_AMQ_DELIVERY_ID = "ddd4j.activemq.deliveryId";

    /**
     * JMS Session 实例
     */
    private final Session session;
    /**
     * JMS 消息实例
     */
    private final Message message;
    /**
     * 投递 ID
     */
    private final long deliveryId;
    /**
     * 消息 ID
     */
    private final String messageId;
    /**
     * 关联 ID
     */
    private final String correlationId;
    /**
     * \u8bb0\u5f55\u5df2\u786e\u8ba4\u6d88\u606f\u7684\u72b6\u6001\u6216\u7ed3\u679c\u3002
     */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 ActiveMQ 消息确认实例。
     *
     * @param session       JMS Session
     * @param message       JMS 消息
     * @param deliveryId    投递 ID
     * @param messageId     消息 ID
     * @param correlationId 关联 ID
     */
    public ActiveMQAcknowledgment(Session session, Message message,
                                  long deliveryId, String messageId, String correlationId) {
        this.session = session;
        this.message = message;
        this.deliveryId = deliveryId;
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
     * <p>ActiveMQ 实现调用 <code>acknowledged.get()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(session) &amp;&amp; !acknowledged.get()</code>
     */
    @Override
    public boolean isOpen() {
        return Objects.nonNull(session) && !acknowledged.get();
    }

    /**
     * 判断当前投递是否已完成确认处理。
     *
     * <p>ActiveMQ 实现调用 <code>acknowledged.get()</code>。</p>
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
        return BrokerType.ACTIVEMQ;
    }

    /**
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>ActiveMQ 实现调用 <code>ack(false)</code>。</p>
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
        runOnce(message::acknowledge);
    }

    /**
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>ActiveMQ 实现调用 <code>nack(false, requeue)</code>。</p>
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
     * @param requeue  是否重新入队
     */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        // JMS 没有 native 的 nack(requeue)，通过 Session.recover() 把已消费但未 ack 的消息重投
        if (requeue) {
            runOnce(session::recover);
        } else {
            // requeue=false 等价于 ack（不重新入队，通常进 DLQ）
            runOnce(message::acknowledge);
        }
    }

    /**
     * 拒绝当前投递，并按约定处理是否重新入队。
     *
     * <p>ActiveMQ 实现调用 <code>nack(false, requeue)</code>。</p>
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
     * @param requeue 是否重新入队
     */
    @Override
    public void recover(boolean requeue) {
        runOnce(session::recover);
    }

    /**
     * 取出适配器持有的底层投递或客户端对象。
     *
     * <p>ActiveMQ 实现调用 <code>Optional.empty()</code>。</p>
     *
     * @param <T>        类型参数
     * @param nativeType 原生类型
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(nativeType.cast(session))</code>、<code>Optional.of(nativeType.cast(message))</code>
     */
    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        if (Objects.isNull(nativeType)) {
            return Optional.empty();
        }
        if (nativeType.isInstance(session)) {
            return Optional.of(nativeType.cast(session));
        }
        if (nativeType.isInstance(message)) {
            return Optional.of(nativeType.cast(message));
        }
        if (nativeType.isInstance(this)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 确保确认操作只执行一次，防止重复 ack/nack。
     *
     * @param op 要执行的 IO 操作
     * @throws UnsupportedOperationException 如果消息已被确认
     * @throws IllegalStateException         如果确认操作失败
     */
    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException(
                    "ActiveMQ message already acknowledged, deliveryId=" + deliveryId);
        }
        try {
            op.run();
        } catch (JMSException ex) {
            acknowledged.set(false);
            throw new IllegalStateException(
                    "ActiveMQ acknowledgment failed, deliveryId=" + deliveryId, ex);
        }
    }

    /**
     * JMS IO 操作函数式接口，用于包装可能抛出 {@link JMSException} 的操作。
     */
    @FunctionalInterface
    private interface IoOperation {
        /**
         * 执行 IO 操作。
         *
         * @throws JMSException 如果操作失败
         */
        void run() throws JMSException;
    }
}
