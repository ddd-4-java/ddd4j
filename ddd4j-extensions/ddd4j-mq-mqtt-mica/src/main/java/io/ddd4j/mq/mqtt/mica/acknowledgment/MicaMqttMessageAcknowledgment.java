package io.ddd4j.mq.mqtt.mica.acknowledgment;

import io.ddd4j.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.mq.acknowledgment.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 mica-mqtt QoS 的消息确认实现。
 * <p>
 * QoS 0 无 Broker 级确认；QoS 1/2 在监听器正常返回后由 mica 客户端自动发送 PUBACK，
 * 本实现将 {@link #ack()} 映射为业务处理完成标记。
 */
@Slf4j
public final class MicaMqttMessageAcknowledgment implements MessageAcknowledgment {

    private final String topic;
    private final int qos;
    private final String messageId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * 构造 mica MQTT 确认对象。
     *
     * @param topic     订阅主题
     * @param qos       消息 QoS
     * @param messageId 消息 ID（若可用）
     */
    public MicaMqttMessageAcknowledgment(String topic, int qos, String messageId) {
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
        return MQBrokerType.MQTT_MICA;
    }

    @Override
    public void ack() {
        ack(false);
    }

    @Override
    public void ack(boolean multiple) {
        ensureNotAcknowledged();
        if (multiple) {
            log.debug("mica-mqtt ignores multiple ack flag, topic={}, qos={}", topic, qos);
        }
        // 逻辑块：QoS 1/2 的 PUBACK 由 mica 在 listener 返回后自动发送
        acknowledged.set(true);
        log.debug("mica-mqtt business ack completed, topic={}, qos={}", topic, qos);
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        throw new UnsupportedAckOperationException(
                "mica-mqtt does not support AMQP-style nack; use QoS and broker retained message policies instead");
    }

    @Override
    public void reject(boolean requeue) {
        nack(requeue);
    }

    @Override
    public void recover(boolean requeue) {
        throw new UnsupportedAckOperationException("mica-mqtt does not support basicRecover semantics");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType");
        if (MicaMqttMessageAcknowledgment.class.isAssignableFrom(nativeType)) {
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
            throw new IllegalStateException("mica-mqtt message already acknowledged, topic=" + topic);
        }
    }
}
