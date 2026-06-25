package io.ddd4j.boot.cmpt.disruptor.autoconfigure;

import io.ddd4j.boot.cmpt.disruptor.config.DisruptorMQProperties;
import io.ddd4j.boot.cmpt.disruptor.consumer.DisruptorMQConsumerEndpointRegistrar;
import io.ddd4j.boot.cmpt.disruptor.core.DisruptorMQBus;
import io.ddd4j.boot.cmpt.disruptor.core.DisruptorMQEventDispatcher;
import io.ddd4j.boot.cmpt.disruptor.publisher.DisruptorMQEventPublisher;
import io.ddd4j.boot.cmpt.disruptor.spi.DisruptorMQBrokerAdapter;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LMAX Disruptor 本地 MQ 自动配置。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(DisruptorMQBus.class)
@ConditionalOnExpression("${ddd4j.mq.enabled:false} && '${ddd4j.mq.broker:none}' == 'disruptor'")
@EnableConfigurationProperties({Ddd4jMQProperties.class, DisruptorMQProperties.class})
public class Ddd4jDisruptorMQAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DisruptorMQEventDispatcher disruptorMQEventDispatcher() {
        return new DisruptorMQEventDispatcher();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public DisruptorMQBus disruptorMQBus(
            DisruptorMQProperties disruptorMQProperties,
            DisruptorMQEventDispatcher dispatcher) {
        return new DisruptorMQBus(disruptorMQProperties, dispatcher);
    }

    @Bean
    @ConditionalOnMissingBean
    public DisruptorMQConsumerEndpointRegistrar disruptorMQConsumerEndpointRegistrar(
            DisruptorMQBus disruptorMQBus) {
        return new DisruptorMQConsumerEndpointRegistrar(disruptorMQBus);
    }

    @Bean
    @ConditionalOnMissingBean(MQBrokerAdapter.class)
    public DisruptorMQBrokerAdapter disruptorMQBrokerAdapter(
            DisruptorMQBus disruptorMQBus,
            Ddd4jMQProperties properties,
            DisruptorMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new DisruptorMQBrokerAdapter(disruptorMQBus, properties, consumerEndpointRegistrar);
    }

    @Bean
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher disruptorMQEventPublisher(
            DisruptorMQBus disruptorMQBus,
            Ddd4jMQProperties properties) {
        return new DisruptorMQEventPublisher(disruptorMQBus, properties);
    }
}
