package io.ddd4j.boot.cmpt.pulsar.autoconfigure;

import io.ddd4j.boot.cmpt.pulsar.consumer.PulsarConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.pulsar.publisher.PulsarMQEventPublisher;
import io.ddd4j.boot.cmpt.pulsar.spi.PulsarMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.apache.pulsar.client.api.PulsarClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.pulsar.PulsarAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.pulsar.core.PulsarTemplate;

/**
 * Pulsar 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=pulsar 时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(PulsarTemplate.class)
@ConditionalOnExpression("'${ddd4j.mq.enabled:false}' == 'true' && '${ddd4j.mq.broker:none}' == 'pulsar'")
@AutoConfigureAfter(PulsarAutoConfiguration.class)
@EnableConfigurationProperties(Ddd4jMQProperties.class)
public class Ddd4jPulsarMQAutoConfiguration {

    /**
     * 注册 Pulsar 消费端点编排器（依赖 Spring Boot 提供的 {@link PulsarClient}）。
     */
    @Bean
    @ConditionalOnBean(PulsarClient.class)
    @ConditionalOnMissingBean
    public PulsarConsumerEndpointRegistrar pulsarConsumerEndpointRegistrar(
            ApplicationContext applicationContext,
            Ddd4jMQProperties properties) {
        return new PulsarConsumerEndpointRegistrar(applicationContext, properties);
    }

    /**
     * 注册 Pulsar Broker 适配器。
     */
    @Bean
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
    public PulsarMQBrokerAdapter pulsarMQBrokerAdapter(
            PulsarTemplate<String> pulsarTemplate,
            Ddd4jMQProperties properties,
            PulsarConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new PulsarMQBrokerAdapter(pulsarTemplate, properties, consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher pulsarMQEventPublisher(
            PulsarTemplate<String> pulsarTemplate,
            Ddd4jMQProperties properties) {
        return new PulsarMQEventPublisher(pulsarTemplate, properties);
    }
}
