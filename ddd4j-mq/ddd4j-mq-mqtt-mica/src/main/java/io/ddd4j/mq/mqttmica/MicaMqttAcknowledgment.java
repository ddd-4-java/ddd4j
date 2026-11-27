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
package io.ddd4j.mq.mqttmica;

import io.ddd4j.mq.BrokerType;
import io.ddd4j.mq.message.Acknowledgment;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mica-mqtt 手动确认映射实现。
 *
 * <p>mica-mqtt 在 QoS &gt; 0 时由内部 PUBACK 链路自动处理；本 ack 端口仅作为"已处理"标记位。
 * 请求 Broker 重投时显式抛出异常，不将消息伪装成已确认。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MicaMqttAcknowledgment implements Acknowledgment {

    /**
     * Header 键：mica-mqtt 消息 ID
     */
    public static final String HEADER_MICA_MESSAGE_ID = "ddd4j.mica.messageId";
    /**
     * Header 键：mica-mqtt 主题
     */
    public static final String HEADER_MICA_TOPIC = "ddd4j.mica.topic";

    /**
     * mica-mqtt 消息 ID
     */
    private final long messageId;
    /**
     * mica-mqtt 主题
     */
    private final String topic;
    /**
     * 关联 ID
     */
    private final String correlationId;
    /**
     * 确认状态标记
     */
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 mica-mqtt 消息确认实例。
     *
     * @param messageId     消息 ID
     * @param topic         主题
     * @param correlationId 关联 ID
     */
    public MicaMqttAcknowledgment(long messageId, String topic, String correlationId) {
        this.messageId = messageId;
        this.topic = topic;
        this.correlationId = correlationId;
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
     * <p>MQTT 实现调用 <code>Long.toString(messageId)</code>。</p>
     *
     * @return 当前消息的标识
     */
    @Override
    public String messageId() {
        return Long.toString(messageId);
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
     * @return 此实现返回<code>true</code>
     */
    @Override
    public boolean isOpen() {
        return true;
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
        return BrokerType.MQTT_MICA;
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
     * <p>MQTT 实现调用 <code>runOnce()</code>。</p>
     *
     * @param multiple 是否批量确认至当前 tag
     */
    @Override
    public void ack(boolean multiple) {
        runOnce();
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
     * 向底层客户端报告本次投递处理失败，并按调用选项决定是否请求重新投递。
     *
     * <p>满足 <code>requeue</code> 时抛出 <code>UnsupportedOperationException</code>。 MQTT 实现调用 <code>runOnce()</code>。</p>
     *
     * @param multiple 是否批量否定
     * @param requeue  是否重新入队
     */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (requeue) {
            throw new UnsupportedOperationException("mica-mqtt does not support broker redelivery");
        }
        // mica 客户端层无原生 nack；不重投时仅标记已处理。
        runOnce();
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
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(type.cast(this))</code>
     */
    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (Objects.isNull(type)) {
            return Optional.empty();
        }
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回 mica-mqtt 主题。
     *
     * @return 消息主题
     */
    public String topic() {
        return topic;
    }

    /**
     * 占用当前消息的一次确认机会。
     *
     * @throws UnsupportedOperationException 当前消息的确认机会已经使用
     */
    private void runOnce() {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("mica-mqtt message already ack'd, id=" + messageId);
        }
    }
}
