package io.ddd4j.mq.rabbit.autoconfigure;

import io.ddd4j.mq.rabbit.consumer.RabbitMQConsumerEndpointRegistrar;
import io.ddd4j.mq.rabbit.publisher.RabbitMQEventPublisher;
import io.ddd4j.mq.rabbit.spi.RabbitMQBrokerAdapter;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=rabbit 时生效。
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jRabbitMQAutoConfiguration {

    /**
     * 注册 Rabbit 消费端点编排器。
     */
    @Bean
    public RabbitMQConsumerEndpointRegistrar rabbitMQConsumerEndpointRegistrar(
            ApplicationContext applicationContext,
            RabbitListenerEndpointRegistry endpointRegistry,
            Ddd4jMQProperties properties) {
        return new RabbitMQConsumerEndpointRegistrar(applicationContext, endpointRegistry, properties);
    }

    /**
     * 注册 RabbitMQ Broker 适配器。
     */
    @Bean
    public RabbitMQBrokerAdapter rabbitMQBrokerAdapter(
            RabbitTemplate rabbitTemplate,
            Ddd4jMQProperties properties,
            RabbitMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new RabbitMQBrokerAdapter(rabbitTemplate, properties, consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    public MQEventPublisher rabbitMQEventPublisher(
            RabbitTemplate rabbitTemplate,
            Ddd4jMQProperties properties) {
        return new RabbitMQEventPublisher(rabbitTemplate, properties);
    }
}
