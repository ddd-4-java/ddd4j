package io.ddd4j.boot.mq.spi;

import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.registry.MQBrokerType;

/**
 * 发布器工厂 SPI：按 Broker 类型创建 {@link MQEventPublisher}。
 */
public interface MQPublisherFactory {

    /**
     * @return 本工厂支持的 Broker 类型
     */
    MQBrokerType brokerType();

    /**
     * 创建发布器实例。
     *
     * @param properties MQ 配置
     * @return 发布端口
     */
    MQEventPublisher createPublisher(Ddd4jMQProperties properties);

    /**
     * 是否支持指定 Broker 类型。
     *
     * @param brokerType Broker 类型
     * @return 支持时 true
     */
    default boolean supports(MQBrokerType brokerType) {
        return this.brokerType() == brokerType;
    }
}
