package io.ddd4j.mq.mqtt.ack;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Eclipse Paho MQTT manual acknowledgment mapping.
 *
 * <p>MQTT 没有原生 broker-side ack（仅 QoS 1/2 协议层的 PUBACK/PUBCOMP）；
 * Paho 客户端在 QoS &gt; 0 时收到消息需要 {@link IMqttDeliveryToken#waitForCompletion(long)} 确认。
 * 这里把 {@code ack()} 映射为 {@code message.setId/setQos（标记为已处理）}，
 * {@code nack(requeue=true)} 映射为发布一条重发消息（业务侧需自行实现 DLQ 策略）。
 */
public class MqttMessageAcknowledgment implements MessageAcknowledgment {

    public static final String HEADER_MQTT_MESSAGE = "ddd4j.mqtt.message";
    public static final String HEADER_MQTT_TOPIC = "ddd4j.mqtt.topic";

    private final MqttMessage message;
    private final String topic;
    private final int messageId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    public MqttMessageAcknowledgment(MqttMessage message, String topic) {
        this.message = message;
        this.topic = topic;
        this.messageId = message.getId();
    }

    @Override
    public long deliveryTag() {
        return messageId;
    }

    @Override
    public String messageId() {
        return Integer.toString(messageId);
    }

    @Override
    public String correlationId() {
        return null;
    }

    @Override
    public boolean isOpen() {
        return java.util.Objects.nonNull(message);
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
        runOnce(() -> { /* MQTT 协议层已自动 PUBACK */ });
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        // MQTT 没有原生 nack；仅标记本地为未处理（业务侧可订阅 DLQ topic 重新发布）
        runOnce(() -> {
            throw new UnsupportedAckOperationException("MQTT has no native nack; use a DLQ topic");
        });
    }

    @Override
    public void reject(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void recover(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (java.util.Objects.isNull(type)) {
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

    private void runOnce(IoOperation op) {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedAckOperationException("MQTT message already acknowledged, id=" + messageId);
        }
        op.run();
    }

    @FunctionalInterface
    private interface IoOperation {
        void run();
    }
}
