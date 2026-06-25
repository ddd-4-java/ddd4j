package io.ddd4j.boot.cmpt.rocket.autoconfigure;

import io.ddd4j.boot.cmpt.rocket.consumer.RocketMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.rocket.publisher.RocketMQEventPublisher;
import io.ddd4j.boot.cmpt.rocket.spi.RocketMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=rocket 时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnExpression("'${ddd4j.mq.enabled:false}' == 'true' && '${ddd4j.mq.broker:none}' == 'rocket'")
@AutoConfigureAfter(RocketMQAutoConfiguration.class)
@EnableConfigurationProperties(Ddd4jMQProperties.class)
public class Ddd4jRocketMQAutoConfiguration {

    /**
     * 注册 RocketMQ 消费端点编排器。
     */
    @Bean
    @ConditionalOnMissingBean
    public RocketMQConsumerEndpointRegistrar rocketMQConsumerEndpointRegistrar(
            ApplicationContext applicationContext,
            Ddd4jMQProperties properties) {
        return new RocketMQConsumerEndpointRegistrar(applicationContext, properties);
    }

    /**
     * 注册 RocketMQ Broker 适配器。
     */
    @Bean
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
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
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher rocketMQEventPublisher(
            RocketMQTemplate rocketMQTemplate,
            Ddd4jMQProperties properties) {
        return new RocketMQEventPublisher(rocketMQTemplate, properties);
    }
}
