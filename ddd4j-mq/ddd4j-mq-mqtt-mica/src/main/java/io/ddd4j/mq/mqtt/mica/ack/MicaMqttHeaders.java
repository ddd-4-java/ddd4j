package io.ddd4j.mq.mqtt.mica.ack;

/**
 * mica-mqtt 消息头键名（写入 {@link io.ddd4j.mq.contract.MQMessage} headers）。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
