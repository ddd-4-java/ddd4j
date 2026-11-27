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

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return messageId;
    }

    /** {@inheritDoc} */
    @Override
    public String messageId() {
        return Integer.toString(messageId);
    }

    /** {@inheritDoc} */
    @Override
    public String correlationId() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOpen() {
        return Objects.nonNull(message);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    /** {@inheritDoc} */
    @Override
    public BrokerType brokerType() {
        return BrokerType.MQTT;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public void ack(boolean multiple) {
        runOnce(() -> { /* MQTT 协议层已自动 PUBACK */ });
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        // MQTT 没有原生 nack；失败时保留未确认状态，业务侧可自行发布到 DLQ。
        throw new UnsupportedOperationException("MQTT has no native nack; use a DLQ topic");
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
     * \u5360\u7528\u5f53\u524d\u6d88\u606f\u7684\u4e00\u6b21\u786e\u8ba4\u673a\u4f1a\uff0c\u968f\u540e\u6267\u884c\u539f\u751f\u52a8\u4f5c\u3002
     * @param op \u672c\u6b21\u8981\u6267\u884c\u7684\u539f\u751f\u6d88\u606f\u786e\u8ba4\u6216\u62d2\u7edd\u64cd\u4f5c
     * @throws UnsupportedOperationException \u5f53\u524d\u6d88\u606f\u7684\u786e\u8ba4\u673a\u4f1a\u5df2\u7ecf\u4f7f\u7528
     */
    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("MQTT message already acknowledged, id=" + messageId);
        }
        op.run();
    }

    /** \u8868\u793a\u6d88\u606f\u786e\u8ba4\u9002\u914d\u5668\u6267\u884c\u7684\u5e95\u5c42\u4ee3\u7406\u64cd\u4f5c\u3002 */
    @FunctionalInterface
    private interface IoOperation {
        /** \u6267\u884c\u4e00\u6b21\u539f\u751f\u4ee3\u7406\u7684\u6d88\u606f\u786e\u8ba4\u64cd\u4f5c\u3002 */
        void run();
    }
}
