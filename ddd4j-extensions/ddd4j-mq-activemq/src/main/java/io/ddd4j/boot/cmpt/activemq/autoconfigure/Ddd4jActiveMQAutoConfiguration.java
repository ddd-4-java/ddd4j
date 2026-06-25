package io.ddd4j.boot.cmpt.activemq.autoconfigure;

import io.ddd4j.boot.cmpt.activemq.consumer.ActiveMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.activemq.publisher.ActiveMQEventPublisher;
import io.ddd4j.boot.cmpt.activemq.spi.ActiveMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;

/**
 * ActiveMQ Artemis 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=activemq 时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(JmsTemplate.class)
@ConditionalOnExpression("'${ddd4j.mq.enabled:false}' == 'true' && '${ddd4j.mq.broker:none}' == 'activemq'")
@AutoConfigureAfter(ArtemisAutoConfiguration.class)
@EnableConfigurationProperties(Ddd4jMQProperties.class)
public class Ddd4jActiveMQAutoConfiguration {

    /**
     * 注册 ActiveMQ 消费端点编排器。
     */
    @Bean
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
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
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher activeMQEventPublisher(
            JmsTemplate jmsTemplate,
            Ddd4jMQProperties properties) {
        return new ActiveMQEventPublisher(jmsTemplate, properties);
    }
}
