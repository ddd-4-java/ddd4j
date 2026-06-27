package io.ddd4j.mq.rocket.autoconfigure;

import io.ddd4j.mq.rocket.consumer.RocketMQConsumerEndpointRegistrar;
import io.ddd4j.mq.rocket.publisher.RocketMQEventPublisher;
import io.ddd4j.mq.rocket.spi.RocketMQBrokerAdapter;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=rocket 时生效。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jRocketMQAutoConfiguration {

    /**
     * 注册 RocketMQ 消费端点编排器。
     */
    @Bean
    public RocketMQConsumerEndpointRegistrar rocketMQConsumerEndpointRegistrar(
            ApplicationContext applicationContext,
            Ddd4jMQProperties properties) {
        return new RocketMQConsumerEndpointRegistrar(applicationContext, properties);
    }

    /**
     * 注册 RocketMQ Broker 适配器。
     */
    @Bean
    public RocketMQBrokerAdapter rocketMQBrokerAdapter(
            RocketMQTemplate rocketMQTemplate,
            Ddd4jMQProperties properties,
            RocketMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new RocketMQBrokerAdapter(rocketMQTemplate, properties, consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    public MQEventPublisher rocketMQEventPublisher(
            RocketMQTemplate rocketMQTemplate,
            Ddd4jMQProperties properties) {
        return new RocketMQEventPublisher(rocketMQTemplate, properties);
    }
}
