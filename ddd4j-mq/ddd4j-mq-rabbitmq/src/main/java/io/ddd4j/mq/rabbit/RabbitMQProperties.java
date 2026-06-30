package io.ddd4j.mq.rabbit;

import com.rabbitmq.client.ConnectionFactory;

/**
 * RabbitMQ adapter configuration.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RabbitMQProperties {

    private String host = "localhost";
    private int port = 5672;
    private String username = "guest";
    private String password = "guest";
    private String virtualHost = "/";
    private String exchange = "ddd4j.mq";
    private boolean durable = true;
    private boolean autoDeclare = true;

    public ConnectionFactory connectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        return factory;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
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

    public String getVirtualHost() {
        return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isAutoDeclare() {
        return autoDeclare;
    }

    public void setAutoDeclare(boolean autoDeclare) {
        this.autoDeclare = autoDeclare;
    }
}
