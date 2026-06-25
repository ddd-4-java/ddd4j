package io.ddd4j.boot.cmpt.mqtt.mica.acknowledgment;

/**
 * mica-mqtt 消息头键名（写入 {@link io.ddd4j.boot.mq.contract.MQMessage} headers）。
 */
public final class MicaMqttHeaders {

    /** 接收主题 */
    public static final String TOPIC = "mica.mqtt.topic";

    /** 消息 QoS 等级 */
    public static final String QOS = "mica.mqtt.qos";

    /** 消息 ID（packet id，若可用） */
    public static final String MESSAGE_ID = "mica.mqtt.message-id";

    private MicaMqttHeaders() {
    }
}
