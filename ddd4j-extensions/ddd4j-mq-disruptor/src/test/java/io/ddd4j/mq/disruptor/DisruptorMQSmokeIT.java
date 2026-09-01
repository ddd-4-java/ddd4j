package io.ddd4j.mq.disruptor;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.disruptor.autoconfigure.Ddd4jDisruptorMQAutoConfiguration;
import io.ddd4j.mq.disruptor.config.DisruptorMQProperties;
import io.ddd4j.mq.spi.MQEventPublisherContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Disruptor 本地 MQ 冒烟测试（无 Testcontainers：进程内 RingBuffer，无需外部 Broker）。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        DisruptorMQSmokeIT.DisruptorPropertiesConfiguration.class,
        Ddd4jDisruptorMQAutoConfiguration.class
})
class DisruptorMQSmokeIT {

    @Autowired
    private MQEventPublisherContract mqEventPublisher;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "disruptor");
        registry.add("ddd4j.mq.namespace", () -> "it");
    }

    @Test
    void publishShouldNotThrow() {
        assertNotNull(mqEventPublisher);

        DemoPublishEvent event = new DemoPublishEvent();
        event.setTopic("smoke");
        event.setTag("ping");
        event.setTenantId("tenant-it");

        assertDoesNotThrow(() -> mqEventPublisher.publish(
                event,
                MQDestination.of("smoke", "ping", "it")));
    }

    /**
     * Disruptor 模块属性（使用默认值即可）。
     */
    @Configuration(proxyBeanMethods = false)
    static class DisruptorPropertiesConfiguration {

        /**
         * 注册 Disruptor 子配置 Bean。
         */
        @Bean
        DisruptorMQProperties disruptorMQProperties() {
            return new DisruptorMQProperties();
        }
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
