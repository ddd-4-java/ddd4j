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
     * 创建事件发布器。
     *
     * <p>适配器必须显式提供发布器，避免遗漏实现后在运行期产生延迟 NPE。
     *
     * @param props MQ 配置（预留，当前未使用）
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
     * @param message 消息信封（{@link MQMessage}，纯 Java 模型）
     * @return 确认端口
     */
    MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message);

    /**
     * 是否支持当前配置的 Broker 类型。
     */
    boolean supports(MQBrokerType configured);
}
