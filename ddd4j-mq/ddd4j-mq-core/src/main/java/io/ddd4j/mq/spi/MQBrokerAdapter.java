package io.ddd4j.mq.spi;

import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.registry.MQBrokerType;
import io.ddd4j.mq.registry.MQListenerDefinition;
import org.springframework.messaging.Message;

/**
 * Broker 适配 SPI：各 {@code ddd4j-cmpt-*} 模块实现并注册到 Spring 容器。
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
     * 从消息信封解析确认端口（推荐使用 {@link Message} 参数版本）。
     * <p>
     * 实现模块应从 {@code Message.getHeaders()} 中提取原生消息（如 Channel、Session 等），
     * 并通过 {@link io.ddd4j.mq.contract.MQMessages#nativeMessage(Message, Class)} 获取。
     *
     * @param message 消息信封（{@link Message}）
     * @return 确认端口
     */
    default MessageAcknowledgment resolveAcknowledgment(Message<?> message) {
        return resolveAcknowledgment(MQMessage.from(message));
    }

    /**
     * 从消息信封解析确认端口（兼容旧 {@link MQMessage} 参数）。
     * <p>
     * 实现模块覆写此方法即可，新 API 默认委托到此方法。
     *
     * @param message 消息信封（{@link MQMessage}，兼容旧 API）
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
