package io.ddd4j.boot.cmpt.rabbit.autoconfigure;

import io.ddd4j.boot.cmpt.rabbit.consumer.RabbitMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.rabbit.publisher.RabbitMQEventPublisher;
import io.ddd4j.boot.cmpt.rabbit.spi.RabbitMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=rabbit 时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnExpression("${ddd4j.mq.enabled:false} && '${ddd4j.mq.broker:none}' == 'rabbit'")
@AutoConfigureAfter(RabbitAutoConfiguration.class)
@EnableConfigurationProperties(Ddd4jMQProperties.class)
public class Ddd4jRabbitMQAutoConfiguration {

    /**
     * 注册 Rabbit 消费端点编排器。
     */
    @Bean
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
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
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher rabbitMQEventPublisher(
            RabbitTemplate rabbitTemplate,
            Ddd4jMQProperties properties) {
        return new RabbitMQEventPublisher(rabbitTemplate, properties);
    }
}
