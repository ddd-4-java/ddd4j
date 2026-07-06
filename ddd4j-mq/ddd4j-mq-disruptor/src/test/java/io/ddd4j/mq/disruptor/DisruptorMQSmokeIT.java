package io.ddd4j.mq.disruptor;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.config.MQProperties;
import io.ddd4j.mq.message.Destination;
import io.ddd4j.mq.disruptor.autoconfigure.Ddd4jDisruptorMQAutoConfiguration;
import io.ddd4j.mq.disruptor.config.DisruptorMQProperties;
import io.ddd4j.mq.event.MQEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Disruptor 本地 MQ 冒烟测试（无 Testcontainers：进程内 RingBuffer，无需外部 Broker）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        DisruptorMQSmokeIT.DisruptorPropertiesConfiguration.class,
        Ddd4jDisruptorMQAutoConfiguration.class
})
class DisruptorMQSmokeIT {

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Test
    void publishShouldNotThrow() {
        assertNotNull(mqEventPublisher);

        DemoPublishEvent event = new DemoPublishEvent();
        event.setTopic("smoke");
        event.setTag("ping");
        event.setTenantId("tenant-it");

        assertDoesNotThrow(() -> mqEventPublisher.publish(
                event,
                Destination.of("smoke", "ping", "it")));
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
        MQProperties ddd4jMQProperties() {
            MQProperties properties = new MQProperties();
            properties.setEnabled(true);
            properties.setBroker("disruptor");
            properties.setNamespace("it");
            return properties;
        }

        @Bean
        DisruptorMQProperties disruptorMQProperties() {
            return new DisruptorMQProperties();
        }
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
