package io.ddd4j.mq.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;

/**
 * Broker 适配 SPI（纯 Java，零 Spring 依赖）。
 *
 * <p>各 {@code ddd4j-mq-*} 模块实现并通过三框架适配层注册到运行时容器
 * （Spring / Quarkus / Javalin）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface MQBrokerAdapter {

    /**
     * @return 本 Adapter 对应的 Broker 类型
     */
    MQBrokerType brokerType();

    /**
     * 返回本适配器的发布器实例。
     *
     * <p>如适配器不提供发布能力（如纯消费端点），默认抛出 UnsupportedOperationException。
     *
     * @param props MQ 配置
     * @return 发布端口实现
     */
    default MQEventPublisher createPublisher(Ddd4jMQProperties props) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not provide an MQEventPublisher");
    }

    /**
     * 注册消费端点（委托底层 Broker 客户端，如 Kafka Consumer / RabbitMQ Channel）。
     *
     * @param definition 监听器定义
     * @param handler    消费处理器
     */
    void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler);

    /**
     * 从消息信封解析确认端口。
     *
     * <p>实现模块应从 {@link MQMessage#nativeMessage()} 逃生口获取底层 Broker 原生对象
     * （如 Kafka RecordMetadata、RabbitMQ Envelope），并构建对应的 {@link MessageAcknowledgment}。
     *
     * <p>默认返回 null，表示适配器不提供 ACK 解析能力，
     * 由 {@link io.ddd4j.mq.consume.MQConsumeEngine} 回退到 Broker Registrar 传入的 ack 或 NoOp。
     *
     * @param message 消息信封（{@link MQMessage}，纯 Java 模型）
     * @return 确认端口，null 表示不提供
     */
    default MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message) {
        return null;
    }

    /**
     * 是否支持当前配置的 Broker 类型。
     */
    boolean supports(MQBrokerType configured);
}
