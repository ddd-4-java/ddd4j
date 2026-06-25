package io.ddd4j.boot.cmpt.mqtt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 连接与发布订阅配置（前缀 {@code ddd4j.mq.mqtt}）。
 * <p>
 * 连接参数与 sample {@code ddd4j-sample-starter-druid-mqtt-client1} 对齐（Eclipse Paho）。
 */
@Data
@ConfigurationProperties(prefix = "ddd4j.mq.mqtt")
public class Ddd4jMqttProperties {

    /** Broker 地址，如 {@code tcp://127.0.0.1:1883} */
    private String url = "tcp://127.0.0.1:1883";

    /** 发布端客户端 ID 前缀（实际 ID 会追加随机后缀保证唯一） */
    private String publishClientIdPrefix = "ddd4j-mqtt-pub";

    /** 订阅端客户端 ID 前缀 */
    private String subscribeClientIdPrefix = "ddd4j-mqtt-sub";

    /** 认证用户名（可选） */
    private String username;

    /** 认证密码（可选） */
    private String password;

    /** 默认 QoS：0=至多一次，1=至少一次，2=恰好一次 */
    private int qos = 1;

    /** 是否清除 session（对应 Paho cleanSession） */
    private boolean cleanSession = true;

    /** 连接超时（秒） */
    private int connectionTimeout = 60;

    /** 心跳间隔（秒） */
    private int keepAliveInterval = 20;

    /** 是否自动重连 */
    private boolean automaticReconnect = true;

    /** 最大重连间隔（毫秒） */
    private int maxReconnectDelay = 5000;

    /** 入站适配器 completion 超时（毫秒） */
    private long completionTimeout = 5000L;

    /** 发布是否异步（委托 {@link org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler}） */
    private boolean asyncPublish = true;
}
