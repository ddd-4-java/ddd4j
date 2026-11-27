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

    /** {@inheritDoc} */
    @Override
    public long deliveryTag() {
        return messageId;
    }

    /** {@inheritDoc} */
    @Override
    public String messageId() {
        return Long.toString(messageId);
    }

    /** {@inheritDoc} */
    @Override
    public String correlationId() {
        return correlationId;
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
        return BrokerType.MQTT_MICA;
    }

    /** {@inheritDoc} */
    @Override
    public void ack() {
        ack(false);
    }

    /** {@inheritDoc} */
    @Override
    public void ack(boolean multiple) {
        runOnce();
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    /** {@inheritDoc} */
    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (requeue) {
            throw new UnsupportedOperationException("mica-mqtt does not support broker redelivery");
        }
        // mica 客户端层无原生 nack；不重投时仅标记已处理。
        runOnce();
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
     * \u5360\u7528\u5f53\u524d\u6d88\u606f\u7684\u4e00\u6b21\u786e\u8ba4\u673a\u4f1a\u3002
     * @throws UnsupportedOperationException \u5f53\u524d\u6d88\u606f\u7684\u786e\u8ba4\u673a\u4f1a\u5df2\u7ecf\u4f7f\u7528
     */
    private void runOnce() {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("mica-mqtt message already ack'd, id=" + messageId);
        }
    }
}
