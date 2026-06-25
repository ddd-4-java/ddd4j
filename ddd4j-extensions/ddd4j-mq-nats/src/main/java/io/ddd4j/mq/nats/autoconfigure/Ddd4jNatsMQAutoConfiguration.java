package io.ddd4j.mq.nats.autoconfigure;

import io.ddd4j.mq.nats.consumer.NatsMQConsumerEndpointRegistrar;
import io.ddd4j.mq.nats.publisher.NatsMQEventPublisher;
import io.ddd4j.mq.nats.spi.NatsMQBrokerAdapter;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * NATS 组件自动配置，在 {@code ddd4j.mq.enabled=true} 且 broker=nats 时生效。
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jNatsMQAutoConfiguration {

    /**
     * 注册 NATS 连接（骨架 Bean，可通过自定义 Connection 覆盖）。
     *
     * @param servers NATS 服务地址，默认 nats://127.0.0.1:4222
     * @return NATS 连接
     */
    @Bean(destroyMethod = "close")
    public Connection natsConnection(
            @org.springframework.beans.factory.annotation.Value("${ddd4j.mq.nats.servers:nats://127.0.0.1:4222}") String servers)
            throws IOException, InterruptedException {
        Options options = new Options.Builder().server(servers).build();
        return Nats.connect(options);
    }

    /**
     * 注册 NATS 消费端点编排器。
     */
    @Bean(destroyMethod = "close")
    public NatsMQConsumerEndpointRegistrar natsMQConsumerEndpointRegistrar(
            ObjectProvider<Connection> connectionProvider,
            Ddd4jMQProperties properties) {
        return new NatsMQConsumerEndpointRegistrar(connectionProvider.getIfAvailable(), properties);
    }

    /**
     * 注册 NATS Broker 适配器。
     */
    @Bean
    public NatsMQBrokerAdapter natsMQBrokerAdapter(
            ObjectProvider<Connection> connectionProvider,
            Ddd4jMQProperties properties,
            NatsMQConsumerEndpointRegistrar consumerEndpointRegistrar) {
        return new NatsMQBrokerAdapter(connectionProvider.getIfAvailable(), properties, consumerEndpointRegistrar);
    }

    /**
     * 注册领域事件发布 Bean。
     */
    @Bean
    public MQEventPublisher natsMQEventPublisher(
            ObjectProvider<Connection> connectionProvider,
            Ddd4jMQProperties properties) {
        return new NatsMQEventPublisher(connectionProvider.getIfAvailable(), properties);
    }
}
