package io.ddd4j.boot.cmpt.mqtt.mica.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * mica-mqtt 客户端扩展配置（前缀 {@code ddd4j.mq.mica}）。
 * <p>
 * 连接参数与 sample {@code ddd4j-sample-starter-druid-mqtt-client2} 的 {@code mqtt.client.*} 对齐；
 * 本模块发布/订阅 QoS 等 ddd4j 语义在此配置，Broker 地址等仍由 {@code mqtt.client.*} 提供。
 */
@Data
@ConfigurationProperties(prefix = "ddd4j.mq.mica")
public class Ddd4jMicaMqttProperties {

    /**
     * 便捷 Broker 地址（如 {@code tcp://127.0.0.1:1883}），仅作文档/测试辅助；
     * 运行时连接以 {@code mqtt.client.ip}/{@code mqtt.client.port} 为准。
     */
    private String url = "tcp://127.0.0.1:1883";

    /** 默认 QoS：0=至多一次，1=至少一次，2=恰好一次 */
    private int qos = 1;

    /** 是否清除 session（对应 mica {@code mqtt.client.clean-start}） */
    private boolean cleanStart = true;

    /** 认证用户名（可选，映射 {@code mqtt.client.username}） */
    private String username;

    /** 认证密码（可选，映射 {@code mqtt.client.password}） */
    private String password;

    /** 客户端 ID 前缀提示（实际 ID 由 {@code mqtt.client.client-id} 配置） */
    private String clientIdPrefix = "ddd4j-mica-mqtt";
}
