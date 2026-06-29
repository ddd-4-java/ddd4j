package io.ddd4j.mq.tdmq.autoconfigure;

import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.tdmq.client.TdmqClient;
import io.ddd4j.mq.tdmq.client.TdmqClientPlaceholder;
import io.ddd4j.mq.tdmq.consumer.TdmqMQConsumerEndpointRegistrar;
import io.ddd4j.mq.tdmq.publisher.TdmqMQEventPublisher;
import io.ddd4j.mq.tdmq.spi.TdmqMQBrokerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 TDMQ 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=tdmq 时生效。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jTdmqMQAutoConfiguration {

    /**
     * 注册 TDMQ 客户端占位 Bean（可通过自定义 TdmqClient 覆盖）。
     */
    @Bean
    public TdmqClient tdmqClient() {
        return new TdmqClientPlaceholder();
    }

    /**
     * 注册 TDMQ 消费端点编排器。
     */
    @Bean
    public TdmqMQConsumerEndpointRegistrar tdmqMQConsumerEndpointRegistrar(
            TdmqClient tdmqClient,
            Ddd4jMQProperties properties) {
        return new TdmqMQConsumerEndpointRegistrar(tdmqClient, properties);
    }

    /**
     * 注册 TDMQ Broker 适配器。
     */
    @Bean
    public TdmqMQBrokerAdapter tdmqMQBrokerAdapter(
            TdmqClient tdmqClient,
            Ddd4jMQProperties properties,
            TdmqMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new TdmqMQBrokerAdapter(tdmqClient, properties, consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    public MQEventPublisher tdmqMQEventPublisher(TdmqClient tdmqClient, Ddd4jMQProperties properties) {
        return new TdmqMQEventPublisher(tdmqClient, properties);
    }
}
