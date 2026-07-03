package io.ddd4j.mq.mqttmica.spi;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;

import java.util.Objects;
import java.util.UUID;

/**
 * mica-mqtt AIO 客户端配置（纯 Java，零 Spring 依赖）。
 *
 * <p>版本与 {@code ${mica-mqtt.version}} = 2.6.6 对齐。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MicaMqttProperties {

    /** MQTT 服务器 IP */
    private String serverIp = "127.0.0.1";
    /** MQTT 服务器端口 */
    private int port = 1883;
    /** 认证用户名 */
    private String username;
    /** 认证密码 */
    private String password;
    /** 客户端 ID 前缀 */
    private String clientIdPrefix = "ddd4j-mica-";
    /** 是否使用 SSL 连接 */
    private boolean useSsl = false;
    /** 默认 QoS 级别 */
    private int qos = 1;
    /** 心跳保活秒数 */
    private int keepAliveSeconds = 30;
    /** 读取缓冲区大小（字节） */
    private int readBufferSize = 8 * 1024;
    /** 最大未完成消息数 */
    private int maxInflight = 100;

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientIdPrefix() {
        return clientIdPrefix;
    }

    public void setClientIdPrefix(String clientIdPrefix) {
        this.clientIdPrefix = clientIdPrefix;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public int getMaxInflight() {
        return maxInflight;
    }

    public void setMaxInflight(int maxInflight) {
        this.maxInflight = maxInflight;
    }

    public String newClientId() {
        return (Objects.isNull(clientIdPrefix) ? "ddd4j-mica-" : clientIdPrefix) + UUID.randomUUID();
    }

    public MqttClient client() {
        MqttClientCreator creator = MqttClient.create()
                .ip(serverIp)
                .port(port)
                .username(username)
                .password(password)
                .clientId(newClientId())
                .keepAliveSecs(keepAliveSeconds)
                .readBufferSize(readBufferSize);
        if (useSsl) {
            creator.useSsl();
        }
        return creator.connectSync();
    }

    public MqttQoS mqttQoS() {
        return switch (qos) {
            case 0 -> MqttQoS.QOS0;
            case 2 -> MqttQoS.QOS2;
            default -> MqttQoS.QOS1;
        };
    }
}
