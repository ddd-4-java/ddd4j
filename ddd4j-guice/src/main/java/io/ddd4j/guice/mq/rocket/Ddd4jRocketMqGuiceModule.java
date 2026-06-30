package io.ddd4j.guice.mq.rocket;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.rocketmq.RocketMQBrokerAdapter;
import io.ddd4j.mq.rocketmq.RocketMQConsumerEndpointRegistrar;
import io.ddd4j.mq.rocketmq.RocketMQProperties;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * ddd4j RocketMQ 的 Guice 桥接模块。
 */
@Slf4j
public class Ddd4jRocketMqGuiceModule extends AbstractModule {

    private final RocketMQProperties rocketProperties;
    private final Ddd4jMQProperties mqProperties;

    public Ddd4jRocketMqGuiceModule() {
        this(new RocketMQProperties(), new Ddd4jMQProperties());
    }

    public Ddd4jRocketMqGuiceModule(RocketMQProperties rocketProperties) {
        this(rocketProperties, new Ddd4jMQProperties());
    }

    public Ddd4jRocketMqGuiceModule(RocketMQProperties rocketProperties, Ddd4jMQProperties mqProperties) {
        this.rocketProperties = rocketProperties;
        this.mqProperties = mqProperties;
    }

    @Provides
    @Singleton
    public RocketMQConsumerEndpointRegistrar rocketMQConsumerEndpointRegistrar() {
        RocketMQConsumerEndpointRegistrar registrar = new RocketMQConsumerEndpointRegistrar(rocketProperties);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Closing RocketMQConsumerEndpointRegistrar via JVM hook");
            registrar.close();
        }, "ddd4j-guice-rocketmq-shutdown"));
        return registrar;
    }

    @Provides
    @Singleton
    public MQBrokerAdapter rocketMQBrokerAdapter() {
        return new RocketMQBrokerAdapter(rocketProperties, mqProperties);
    }

    @Provides
    @Singleton
    public MQEventPublisher mqEventPublisher(MQBrokerAdapter brokerAdapter) {
        return brokerAdapter.createPublisher(mqProperties);
    }

    @Provides
    @Singleton
    public RocketMQProperties rocketMQProperties() {
        return rocketProperties;
    }

    @Provides
    @Singleton
    public Ddd4jMQProperties ddd4jMQProperties() {
        return mqProperties;
    }
}
