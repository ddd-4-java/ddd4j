package io.ddd4j.mq.disruptor.autoconfigure;

import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.disruptor.config.DisruptorMQProperties;
import io.ddd4j.mq.disruptor.consumer.DisruptorMQConsumerEndpointRegistrar;
import io.ddd4j.mq.disruptor.core.DisruptorMQBus;
import io.ddd4j.mq.disruptor.core.DisruptorMQEventDispatcher;
import io.ddd4j.mq.disruptor.publisher.DisruptorMQEventPublisher;
import io.ddd4j.mq.disruptor.spi.DisruptorMQBrokerAdapter;
import io.ddd4j.mq.publish.MQEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LMAX Disruptor 本地 MQ 自动配置。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jDisruptorMQAutoConfiguration {

    @Bean
    public DisruptorMQEventDispatcher disruptorMQEventDispatcher() {
        return new DisruptorMQEventDispatcher();
    }

    @Bean(destroyMethod = "shutdown")
    public DisruptorMQBus disruptorMQBus(
            DisruptorMQProperties disruptorMQProperties,
            DisruptorMQEventDispatcher dispatcher) {
        return new DisruptorMQBus(disruptorMQProperties, dispatcher);
    }

    @Bean
    public DisruptorMQConsumerEndpointRegistrar disruptorMQConsumerEndpointRegistrar(
            DisruptorMQBus disruptorMQBus) {
        return new DisruptorMQConsumerEndpointRegistrar(disruptorMQBus);
    }

    @Bean
    public DisruptorMQBrokerAdapter disruptorMQBrokerAdapter(
            DisruptorMQBus disruptorMQBus,
            Ddd4jMQProperties properties,
            DisruptorMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new DisruptorMQBrokerAdapter(disruptorMQBus, properties, consumerEndpointRegistrar);
    }

    @Bean
    public MQEventPublisher disruptorMQEventPublisher(
            DisruptorMQBus disruptorMQBus,
            Ddd4jMQProperties properties) {
        return new DisruptorMQEventPublisher(disruptorMQBus, properties);
    }
}
