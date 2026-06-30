package io.ddd4j.mq.activemq.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import java.util.Objects;

/**
 * ActiveMQ Artemis adapter configuration (Pure Java, zero Spring).
 *
 * <p>对应 {@code org.apache.activemq:artemis-client} 2.x 原生 JMS 客户端。
 * 同时兼容 {@link io.ddd4j.mq.registry.MQBrokerType#ACTIVEMQ}（历史 Classic v5 通过单独引入 {@code activemq-client} 5.x 也可适配）。
 */
public class ActiveMQProperties {

    /** Broker URL（例：{@code tcp://host:61616} 或 {@code failover:(tcp://...)}）。 */
    private String brokerUrl = "tcp://localhost:61616";
    private String username;
    private String password;
    private String clientIdPrefix = "ddd4j-mq-";
    /** 是否在注册时自动创建 queues / topics（Artemis 默认按需自动创建）。 */
    private boolean autoDeclare = true;
    /** 消息默认持久化。 */
    private boolean durable = true;

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getClientIdPrefix() { return clientIdPrefix; }
    public void setClientIdPrefix(String clientIdPrefix) { this.clientIdPrefix = clientIdPrefix; }
    public boolean isAutoDeclare() { return autoDeclare; }
    public void setAutoDeclare(boolean autoDeclare) { this.autoDeclare = autoDeclare; }
    public boolean isDurable() { return durable; }
    public void setDurable(boolean durable) { this.durable = durable; }

    public ActiveMQConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        if (username != null && !username.isBlank()) {
            factory.setUser(username);
        }
        if (password != null && !password.isBlank()) {
            factory.setPassword(password);
        }
        factory.setClientID(Objects.requireNonNullElse(clientIdPrefix, "ddd4j-mq-"));
        return factory;
    }
}
