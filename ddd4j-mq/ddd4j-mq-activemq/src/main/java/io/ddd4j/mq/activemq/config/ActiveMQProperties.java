package io.ddd4j.mq.activemq.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import java.util.Objects;

/**
 * ActiveMQ Artemis 适配器配置（纯 Java，零 Spring 依赖）。
 *
 * <p>对应 {@code org.apache.activemq:artemis-client} 2.x 原生 JMS 客户端。
 * 同时兼容 {@link io.ddd4j.mq.registry.MQBrokerType#ACTIVEMQ}（历史 Classic v5 通过单独引入 {@code activemq-client} 5.x 也可适配）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ActiveMQProperties {

    /**
     * Broker URL（例：{@code tcp://host:61616} 或 {@code failover:(tcp://...)}）。
     */
    private String brokerUrl = "tcp://localhost:61616";
    /** 认证用户名 */
    private String username;
    /** 认证密码 */
    private String password;
    /** 客户端 ID 前缀 */
    private String clientIdPrefix = "ddd4j-mq-";
    /**
     * 是否在注册时自动创建 queues / topics（Artemis 默认按需自动创建）。
     */
    private boolean autoDeclare = true;
    /**
     * 消息默认持久化。
     */
    private boolean durable = true;

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
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

    public boolean isAutoDeclare() {
        return autoDeclare;
    }

    public void setAutoDeclare(boolean autoDeclare) {
        this.autoDeclare = autoDeclare;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    /**
     * 创建并配置 ActiveMQ 连接工厂。
     *
     * @return 配置好的连接工厂实例
     */
    public ActiveMQConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        if (Objects.nonNull(username) && !io.ddd4j.kit.lang.StrKit.isBlank(username)) {
            factory.setUser(username);
        }
        if (Objects.nonNull(password) && !io.ddd4j.kit.lang.StrKit.isBlank(password)) {
            factory.setPassword(password);
        }
        factory.setClientID(Objects.requireNonNullElse(clientIdPrefix, "ddd4j-mq-"));
        return factory;
    }
}
