package io.ddd4j.mq.rabbitmq;

import com.rabbitmq.client.ConnectionFactory;
import io.ddd4j.mq.MQProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RabbitMQ adapter configuration.
 *
 * <p>{@link RabbitMQProperties} extends {@link MQProperties} —— 复用通用字段（namespace / defaultTopic /
 * autoAck / persist / retries / username / password / exchange 等），仅声明 RabbitMQ 专属字段。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RabbitMQProperties extends MQProperties {

    /**
     * Broker 主机
     */
    private String host = "localhost";
    /**
     * Broker 端口
     */
    private int port = 5672;
    /**
     * 虚拟主机
     */
    private String virtualHost = "/";
    /**
     * 队列/绑定是否持久化
     */
    private boolean durable = true;
    /**
     * 是否在注册时自动声明 queue / binding
     */
    private boolean autoDeclare = true;

    /**
     * \u57fa\u4e8e\u672c\u914d\u7f6e\uff08\u542b\u7236\u7c7b username/password\uff09\u521b\u5efa\u539f\u751f {@link ConnectionFactory}\u3002
     *
     * @return \u914d\u7f6e\u5b8c\u6210\u7684 RabbitMQ \u8fde\u63a5\u5de5\u5382
     */
    public ConnectionFactory connectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(getUsername());
        factory.setPassword(getPassword());
        factory.setVirtualHost(virtualHost);
        return factory;
    }
}
