package io.ddd4j.mq.mqtt.spi;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import java.util.Objects;
import java.util.UUID;

/**
 * Eclipse Paho MQTT v3 适配器配置（纯 Java，零 Spring 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MqttMQProperties {

    private String serverUri = "tcp://localhost:1883";
    private String clientIdPrefix = "ddd4j-mq-";
    /**
     * QoS level: 0 (at most once), 1 (at least once), 2 (exactly once).
     */
    private int qos = 1;
    private String username;
    private String password;
    private boolean cleanSession = true;
    private int keepAliveSeconds = 30;
    private int connectionTimeoutSeconds = 30;
    private boolean automaticReconnect = true;
    private int maxInflight = 100;
    private String willTopic;
    private String willPayload;
    private int willQos = 0;
    private boolean willRetained = false;

    public String getServerUri() {
        return serverUri;
    }

    public void setServerUri(String serverUri) {
        this.serverUri = serverUri;
    }

    public String getClientIdPrefix() {
        return clientIdPrefix;
    }

    public void setClientIdPrefix(String clientIdPrefix) {
        this.clientIdPrefix = clientIdPrefix;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
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

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }

    public void setAutomaticReconnect(boolean automaticReconnect) {
        this.automaticReconnect = automaticReconnect;
    }

    public int getMaxInflight() {
        return maxInflight;
    }

    public void setMaxInflight(int maxInflight) {
        this.maxInflight = maxInflight;
    }

    public String getWillTopic() {
        return willTopic;
    }

    public void setWillTopic(String willTopic) {
        this.willTopic = willTopic;
    }

    public String getWillPayload() {
        return willPayload;
    }

    public void setWillPayload(String willPayload) {
        this.willPayload = willPayload;
    }

    public int getWillQos() {
        return willQos;
    }

    public void setWillQos(int willQos) {
        this.willQos = willQos;
    }

    public boolean isWillRetained() {
        return willRetained;
    }

    public void setWillRetained(boolean willRetained) {
        this.willRetained = willRetained;
    }

    public String newClientId() {
        return (Objects.isNull(clientIdPrefix) ? "ddd4j-mq-" : clientIdPrefix) + UUID.randomUUID();
    }

    public MqttConnectOptions connectOptions() {
        MqttConnectOptions o = new MqttConnectOptions();
        o.setCleanSession(cleanSession);
        o.setKeepAliveInterval(keepAliveSeconds);
        o.setConnectionTimeout(connectionTimeoutSeconds);
        o.setAutomaticReconnect(automaticReconnect);
        o.setMaxInflight(maxInflight);
        if (Objects.nonNull(username) && !io.ddd4j.kit.lang.StrKit.isBlank(username)) {
            o.setUserName(username);
        }
        if (Objects.nonNull(password) && !io.ddd4j.kit.lang.StrKit.isBlank(password)) {
            o.setPassword(password.toCharArray());
        }
        if (Objects.nonNull(willTopic) && !io.ddd4j.kit.lang.StrKit.isBlank(willTopic)) {
            o.setWill(willTopic, Objects.isNull(willPayload) ? new byte[0] : willPayload.getBytes(), willQos, willRetained);
        }
        return o;
    }
}
