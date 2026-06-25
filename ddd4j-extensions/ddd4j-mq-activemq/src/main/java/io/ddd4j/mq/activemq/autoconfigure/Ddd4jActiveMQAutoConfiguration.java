package io.ddd4j.mq.activemq.autoconfigure;

import io.ddd4j.mq.activemq.consumer.ActiveMQConsumerEndpointRegistrar;
import io.ddd4j.mq.activemq.publisher.ActiveMQEventPublisher;
import io.ddd4j.mq.activemq.spi.ActiveMQBrokerAdapter;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;

/**
 * ActiveMQ Artemis 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=activemq 时生效。
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jActiveMQAutoConfiguration {

    /**
     * 注册 ActiveMQ 消费端点编排器。
     */
    @Bean
    public ActiveMQConsumerEndpointRegistrar activeMQConsumerEndpointRegistrar(
            ApplicationContext applicationContext,
            JmsListenerEndpointRegistry endpointRegistry,
            Ddd4jMQProperties properties) {
        return new ActiveMQConsumerEndpointRegistrar(applicationContext, endpointRegistry, properties);
    }

    /**
     * 注册 ActiveMQ Broker 适配器。
     */
    @Bean
    public ActiveMQBrokerAdapter activeMQBrokerAdapter(
            JmsTemplate jmsTemplate,
            Ddd4jMQProperties properties,
            ActiveMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new ActiveMQBrokerAdapter(jmsTemplate, properties, consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    public MQEventPublisher activeMQEventPublisher(
            JmsTemplate jmsTemplate,
            Ddd4jMQProperties properties) {
        return new ActiveMQEventPublisher(jmsTemplate, properties);
    }
}
