package io.ddd4j.mq.spi;

import io.ddd4j.mq.consume.ack.Acknowledgment;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.consume.ConsumerHandler;
import io.ddd4j.mq.message.Message;
import io.ddd4j.mq.event.MQEventPublisher;
import io.ddd4j.mq.config.BrokerType;
import io.ddd4j.mq.listener.ListenerDefinition;

/**
 * Broker 适配 SPI（纯 Java，零 Spring 依赖）。
 *
 * <p>各 {@code ddd4j-mq-*} 模块实现并通过三框架适配层注册到运行时容器
 * （Spring / Quarkus / Javalin）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface BrokerAdapter {

    /**
     * @return 本 Adapter 对应的 Broker 类型
     */
    BrokerType brokerType();

    /**
     * 返回本适配器的发布器实例。
     *
     * <p>如适配器不提供发布能力（如纯消费端点），默认抛出 UnsupportedOperationException。
     *
     * @param props MQ 配置
     * @return 发布端口实现
     */
    default MQEventPublisher createPublisher(MQProperties props) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not provide an EventPublisher");
    }

    /**
     * 注册消费端点（委托底层 Broker 客户端，如 Kafka Consumer / RabbitMQ Channel）。
     *
     * @param definition 监听器定义
     * @param handler    消费处理器
     */
    void registerConsumer(ListenerDefinition definition, ConsumerHandler handler);

    /**
     * 从消息信封解析确认端口。
     *
     * <p>实现模块应从 {@link Message#nativeMessage()} 逃生口获取底层 Broker 原生对象
     * （如 Kafka RecordMetadata、RabbitMQ Envelope），并构建对应的 {@link Acknowledgment}。
     *
     * <p>默认返回 null，表示适配器不提供 ACK 解析能力，
     * 由 {@link io.ddd4j.mq.consume.ConsumerEngine} 回退到 Broker Registrar 传入的 ack 或 NoOp。
     *
     * @param message 消息信封（{@link Message}，纯 Java 模型）
     * @return 确认端口，null 表示不提供
     */
    default Acknowledgment resolveAcknowledgment(Message<?> message) {
        return null;
    }

    /**
     * 是否支持当前配置的 Broker 类型。
     */
    boolean supports(BrokerType configured);
}
