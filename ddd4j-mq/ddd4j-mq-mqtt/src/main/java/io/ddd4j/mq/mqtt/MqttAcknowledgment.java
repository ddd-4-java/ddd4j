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
package io.ddd4j.mq.mqtt;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Eclipse Paho MQTT 手动确认映射实现。
 *
 * <p>MQTT 没有原生 broker-side ack（仅 QoS 1/2 协议层的 PUBACK/PUBCOMP）；
 * Paho 客户端在 QoS &gt; 0 时收到消息需要 {@link org.eclipse.paho.client.mqttv3.IMqttDeliveryToken#waitForCompletion(long)} 确认。
 * 这里把 {@code ack()} 映射为标记已处理；MQTT 不支持原生 nack、reject 或 recover，
 * 调用这些操作会抛出异常并保留未确认状态，业务侧需自行实现 DLQ 策略。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MqttAcknowledgment implements Acknowledgment {

    /**
     * Header 键：MQTT 消息体
     */
    public static final String HEADER_MQTT_MESSAGE = "ddd4j.mqtt.message";
    /**
     * Header 键：MQTT 主题
     */
    public static final String HEADER_MQTT_TOPIC = "ddd4j.mqtt.topic";

    /**
     * Paho MQTT 消息实例
     */
    private final MqttMessage message;
    /**
     * MQTT 主题
     */
    private final String topic;
    /**
     * 消息 ID
     */
    private final int messageId;
    /**
     * 确认状态标记
     */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 创建 MQTT 消息确认句柄。
     *
     * @param message MQTT 消息
     * @param topic   消息主题
     */
    public MqttAcknowledgment(MqttMessage message, String topic) {
        this.message = message;
        this.topic = topic;
        this.messageId = message.getId();
    }

    /**
     * 返回本次消息投递的标识。
     *
     * @return 当前投递的标记值
     */
    @Override
    public long deliveryTag() {
        return messageId;
    }

    /**
     * 返回收到的消息标识。
     *
     * <p>MQTT 实现调用 <code>Integer.toString(messageId)</code>。</p>
     *
     * @return 当前消息的标识
     */
    @Override
    public String messageId() {
        return Integer.toString(messageId);
    }

    /**
     * 返回消息所属链路的关联标识。
     *
     * @return 此实现返回<code>null</code>
     */
    @Override
    public String correlationId() {
        return null;
    }

    /**
     * 检查当前投递的确认对象是否仍可用。
     *
     * @return 实际返回<code>Objects.nonNull(message)</code>，作为本次判断结果
     */
    @Override
    public boolean isOpen() {
        return Objects.nonNull(message);
    }

    /**
     * 判断当前投递是否已完成确认处理。
     *
     * <p>MQTT 实现调用 <code>acknowledged.get()</code>。</p>
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
        return BrokerType.MQTT;
    }

    /**
     * 确认本次投递或将已处理的位置通知 broker。
     *
     * <p>MQTT 实现调用 <code>ack(false)</code>。</p>
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
        runOnce(() -> { /* MQTT 协议层已自动 PUBACK */ });
    }

    /**
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>MQTT 实现调用 <code>nack(false, requeue)</code>。</p>
     *
     * @param requeue 是否重新入队
     */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /**
     * 明确拒绝当前实现不支持的消息投递操作。
     *
     * @param multiple 是否批量否定
     * @param requeue  是否重新入队
     */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        // MQTT 没有原生 nack；失败时保留未确认状态，业务侧可自行发布到 DLQ。
        throw new UnsupportedOperationException("MQTT has no native nack; use a DLQ topic");
    }

    /**
     * 拒绝当前投递，并按约定处理是否重新入队。
     *
     * <p>MQTT 实现调用 <code>nack(false, requeue)</code>。</p>
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
     * <p>MQTT 实现调用 <code>nack(false, requeue)</code>。</p>
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
     * <p>MQTT 实现调用 <code>Optional.empty()</code>。</p>
     *
     * @param <T>  类型参数
     * @param type 当前操作用于选择实现或转换结果的类型
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(type.cast(message))</code>、<code>Optional.of(type.cast(this))</code>
     */
    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (Objects.isNull(type)) {
            return Optional.empty();
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
     * 返回 Paho MQTT 主题。
     *
     * @return 消息主题
     */
    public String topic() {
        return topic;
    }

    /**
     * 占用当前消息的一次确认机会，随后执行原生动作。
     *
     * @param op 本次要执行的原生消息确认或拒绝操作
     * @throws UnsupportedOperationException 当前消息的确认机会已经使用
     */
    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("MQTT message already acknowledged, id=" + messageId);
        }
        op.run();
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
