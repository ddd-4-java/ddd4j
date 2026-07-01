package io.ddd4j.mq.mqttmica.ack;

import java.util.Objects;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.UnsupportedAckOperationException;
import io.ddd4j.mq.registry.MQBrokerType;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mica-mqtt manual acknowledgment mapping.
 * mica-mqtt 在 QoS &gt; 0 时由内部 PUBACK 链路自动处理；本 ack 端口仅作为"已处理"标记位。
 */
public class MicaMqttMessageAcknowledgment implements MessageAcknowledgment {

    public static final String HEADER_MICA_MESSAGE_ID = "ddd4j.mica.messageId";
    public static final String HEADER_MICA_TOPIC = "ddd4j.mica.topic";

    private final long messageId;
    private final String topic;
    private final String correlationId;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    public MicaMqttMessageAcknowledgment(long messageId, String topic, String correlationId) {
        this.messageId = messageId;
        this.topic = topic;
        this.correlationId = correlationId;
    }

    @Override
    public long deliveryTag() {
        return messageId;
    }

    @Override
    public String messageId() {
        return Long.toString(messageId);
    }

    @Override
    public String correlationId() {
        return correlationId;
    }

    @Override
    public boolean isOpen() {
        return true;
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
        runOnce();
    }

    @Override
    public void nack(boolean requeue) {
        nack(false, requeue);
    }

    @Override
    public void nack(boolean multiple, boolean requeue) {
        if (requeue) {
            runOnce(); // mica 客户端层无 nack
        } else {
            runOnce();
        }
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
        if (Objects.isNull(type)) {
            return Optional.empty();
        }
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    private void runOnce() {
        if (!acknowledged.compareAndSet(false, true)) {
            throw new UnsupportedAckOperationException("mica-mqtt message already ack'd, id=" + messageId);
        }
    }
}
