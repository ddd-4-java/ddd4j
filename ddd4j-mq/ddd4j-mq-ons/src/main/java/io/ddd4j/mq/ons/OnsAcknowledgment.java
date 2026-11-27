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
package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.message.Acknowledgment;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 阿里云 ONS 手动确认映射实现。
 *
 * <p>ONS 消费通过监听器返回的 {@link Action} 来确认消息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OnsAcknowledgment implements Acknowledgment {

    /**
     * Header 键：ONS 消息体
     */
    public static final String HEADER_ONS_MESSAGE = "ddd4j.ons.message";
    /**
     * Header 键：ONS 消费上下文
     */
    public static final String HEADER_ONS_CONTEXT = "ddd4j.ons.context";

    /**
     * ONS 消费上下文
     */
    private final ConsumeContext context;
    /**
     * ONS 消息实例
     */
    private final Message message;
    /**
     * 消息 ID
     */
    private final String messageId;
    /**
     * 用于关联同一次操作产生的事件或消息的标识。
     */
    private final String correlationId;
    /**
     * 消息 Key
     */
    private final String key;
    /**
     * 消息偏移量
     */
    private final long offset;
    /**
     * 确认状态标记
     */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);
    /**
     * 确认动作（提交/稍后重试）
     */
    private volatile Action action = Action.CommitMessage;

    /**
     * 构造 ONS 消息确认实例。
     *
     * @param context ONS 消费上下文
     * @param message ONS 消息
     */
    public OnsAcknowledgment(ConsumeContext context, Message message) {
        this.context = context;
        this.message = message;
        String stableId = OnsMQClient.messageId(message);
        this.messageId = StrKit.isNotEmpty(stableId) ? stableId : message.getMsgID();
        this.correlationId = message.getUserProperties(MQDeliveryHeaders.CORRELATION_ID);
        this.key = message.getKey();
        this.offset = message.getOffset();
    }

    /**
     * 返回本次消息投递的标识。
     *
     * @return 当前投递的标记值
     */
    @Override
    public long deliveryTag() {
        return offset;
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
     * <p>此实现调用 <code>StrKit.isNotEmpty(correlationId)</code>。</p>
     *
     * @return 当前消息关联的请求或事件标识
     */
    @Override
    public String correlationId() {
        return StrKit.isNotEmpty(correlationId) ? correlationId : key;
    }

    /**
     * 检查当前投递的确认对象是否仍可用。
     *
     * @return 实际返回<code>Objects.nonNull(context)</code>，作为本次判断结果
     */
    @Override
    public boolean isOpen() {
        return Objects.nonNull(context);
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
        return BrokerType.ONS;
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
        runOnce(() -> action = Action.CommitMessage);
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
     * @param multiple 是否批量否定
     * @param requeue  是否重新入队
     */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        runOnce(() -> {
            if (requeue) {
                action = Action.ReconsumeLater;
            } else {
                action = Action.CommitMessage; // no native reject-without-commit path
            }
        });
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
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(type.cast(context))</code>、<code>Optional.of(type.cast(message))</code>
     */
    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (Objects.isNull(type)) {
            return Optional.empty();
        }
        if (type.isInstance(context)) {
            return Optional.of(type.cast(context));
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
     * 占用当前消息的一次确认机会，随后执行原生动作。
     *
     * @param op 本次要执行的原生消息确认或拒绝操作
     * @throws UnsupportedOperationException 当前消息的确认机会已经使用
     */
    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("ONS message already ack'd, id=" + messageId);
        }
        op.run();
    }

    /**
     * @return ONS 消费确认动作
     */
    public Action action() {
        return action;
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
