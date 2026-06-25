package io.ddd4j.boot.cmpt.tdmq.autoconfigure;

import io.ddd4j.boot.cmpt.tdmq.client.TdmqClient;
import io.ddd4j.boot.cmpt.tdmq.client.TdmqClientPlaceholder;
import io.ddd4j.boot.cmpt.tdmq.consumer.TdmqMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.tdmq.publisher.TdmqMQEventPublisher;
import io.ddd4j.boot.cmpt.tdmq.spi.TdmqMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 TDMQ 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=tdmq 时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${ddd4j.mq.enabled:false} == true && '${ddd4j.mq.broker:none}'.equals('tdmq')")
@EnableConfigurationProperties(Ddd4jMQProperties.class)
public class Ddd4jTdmqMQAutoConfiguration {

    /**
     * 注册 TDMQ 客户端占位 Bean（可通过自定义 TdmqClient 覆盖）。
     */
    @Bean
    @ConditionalOnMissingBean(TdmqClient.class)
    public TdmqClient tdmqClient() {
        return new TdmqClientPlaceholder();
    }

    /**
     * 注册 TDMQ 消费端点编排器。
     */
    @Bean
    @ConditionalOnMissingBean
    public TdmqMQConsumerEndpointRegistrar tdmqMQConsumerEndpointRegistrar(
            TdmqClient tdmqClient,
            Ddd4jMQProperties properties) {
        return new TdmqMQConsumerEndpointRegistrar(tdmqClient, properties);
    }

    /**
     * 注册 TDMQ Broker 适配器。
     */
    @Bean
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
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
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher tdmqMQEventPublisher(TdmqClient tdmqClient, Ddd4jMQProperties properties) {
        return new TdmqMQEventPublisher(tdmqClient, properties);
    }
}
