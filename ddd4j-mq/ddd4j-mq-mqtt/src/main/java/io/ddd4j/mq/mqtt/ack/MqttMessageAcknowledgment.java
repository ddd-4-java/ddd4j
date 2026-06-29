package io.ddd4j.mq.mqtt.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 MQTT QoS 的消息确认实现。
 * <p>
 * QoS 0 无 Broker 级确认；QoS 1/2 在 Paho 回调正常返回时由客户端自动发送 PUBACK，
 * 本实现将 {@link #ack()} 映射为业务处理完成标记。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class MqttMessageAcknowledgment implements MessageAcknowledgment {

    private final String topic;
    private final int qos;
    private final String messageId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 MQTT 确认对象。
     *
     * @param topic     订阅主题
     * @param qos       消息 QoS
     * @param messageId 消息 ID（若可用）
     */
    public MqttMessageAcknowledgment(String topic, int qos, String messageId) {
        this.topic = topic;
        this.qos = qos;
        this.messageId = messageId;
    }

    @Override
    public long deliveryTag() {
        return qos;
    }

    @Override
    public String messageId() {
        return messageId;
    }

    @Override
    public String correlationId() {
        return topic;
    }

    @Override
    public boolean isOpen() {
        return !acknowledged.get();
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public MQBrokerType brokerType() {
        return MQBrokerType.MQTT;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("MQTT ignores multiple ack flag, topic={}, qos={}", topic, qos);
        }
        // 逻辑块：QoS 1/2 的 PUBACK 由 Paho 在 messageArrived 返回后自动发送
        acknowledged.set(true);
        log.debug("MQTT business ack completed, topic={}, qos={}", topic, qos);
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        throw new UnsupportedAckOperationException(
                "MQTT does not support AMQP-style nack; use QoS and broker retained message policies instead");
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        throw new UnsupportedAckOperationException("MQTT does not support basicRecover semantics");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (MqttMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
            return Optional.of(nativeType.cast(this));
        }
        return Optional.empty();
    }

    /**
     * 返回消息 QoS 等级。
     */
    public int qos() {
        return qos;
    }

    /**
     * 返回 MQTT 主题。
     */
    public String topic() {
        return topic;
    }

    /**
     * 防止重复确认。
     */
    private void ensureNotAcknowledged() {
        if (acknowledged.get()) {
            throw new IllegalStateException("MQTT message already acknowledged, topic=" + topic);
        }
    }
}
