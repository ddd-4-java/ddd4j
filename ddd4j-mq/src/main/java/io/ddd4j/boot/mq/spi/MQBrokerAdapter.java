package io.ddd4j.boot.mq.spi;

import io.ddd4j.boot.mq.acknowledgment.MessageAcknowledgment;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.consume.MQConsumerHandler;
import io.ddd4j.boot.mq.contract.MQMessage;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.registry.MQBrokerType;
import io.ddd4j.boot.mq.registry.MQListenerDefinition;

/**
 * Broker 适配 SPI：各 {@code ddd4j-cmpt-*} 模块实现并注册到 Spring 容器。
 */
public interface MQBrokerAdapter {

    /**
     * @return 本 Adapter 对应的 Broker 类型
     */
    MQBrokerType brokerType();

    /**
     * 创建事件发布器。
     *
     * @param props MQ 配置
     * @return 发布端口实现
     */
    MQEventPublisher createPublisher(Ddd4jMQProperties props);

    /**
     * 注册消费端点（委托 Spring {@code @RabbitListener} 等）。
     *
     * @param definition 监听器定义
     * @param handler    消费处理器
     */
    void registerConsumer(MQListenerDefinition definition, MQConsumerHandler handler);

    /**
     * 从消息信封解析确认端口。
     *
     * @param message 消息信封
     * @return 确认端口
     */
    MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message);

    /**
     * 是否支持当前配置的 Broker 类型。
     *
     * @param configured 配置中的 Broker 类型
     * @return 支持时 true
     */
    boolean supports(MQBrokerType configured);
}
