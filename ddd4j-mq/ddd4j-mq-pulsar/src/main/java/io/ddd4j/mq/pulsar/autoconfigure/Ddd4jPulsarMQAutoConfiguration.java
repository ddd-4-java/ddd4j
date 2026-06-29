package io.ddd4j.mq.pulsar.autoconfigure;

import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.pulsar.consumer.PulsarConsumerEndpointRegistrar;
import io.ddd4j.mq.pulsar.publisher.PulsarMQEventPublisher;
import io.ddd4j.mq.pulsar.spi.PulsarMQBrokerAdapter;
import org.apache.pulsar.client.api.PulsarClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.pulsar.core.PulsarTemplate;

/**
 * Pulsar 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=pulsar 时生效。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jPulsarMQAutoConfiguration {

    /**
     * 注册 Pulsar 消费端点编排器（依赖 Spring Boot 提供的 {@link PulsarClient}）。
     */
    @Bean
    public PulsarConsumerEndpointRegistrar pulsarConsumerEndpointRegistrar(
            ApplicationContext applicationContext,
            Ddd4jMQProperties properties) {
        return new PulsarConsumerEndpointRegistrar(applicationContext, properties);
    }

    /**
     * 注册 Pulsar Broker 适配器。
     */
    @Bean
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
    public MQEventPublisher pulsarMQEventPublisher(
            PulsarTemplate<String> pulsarTemplate,
            Ddd4jMQProperties properties) {
        return new PulsarMQEventPublisher(pulsarTemplate, properties);
    }
}
