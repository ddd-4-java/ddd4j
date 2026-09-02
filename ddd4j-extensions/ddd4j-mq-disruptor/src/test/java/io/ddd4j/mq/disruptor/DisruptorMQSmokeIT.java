package io.ddd4j.mq.disruptor;

import io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.disruptor.autoconfigure.Ddd4jDisruptorMQAutoConfiguration;
import io.ddd4j.mq.disruptor.config.DisruptorMQProperties;
import io.ddd4j.mq.test.AbstractMqContainerIT;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Disruptor 本地 MQ 冒烟测试（无 Testcontainers：进程内 RingBuffer，无需外部 Broker）。
 * <p>公共骨架（发布者注入、冒烟发布断言）见 {@link AbstractMqContainerIT}。</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        DisruptorMQSmokeIT.DisruptorPropertiesConfiguration.class,
        Ddd4jDisruptorMQAutoConfiguration.class
})
class DisruptorMQSmokeIT extends AbstractMqContainerIT {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerCommonMqProperties(registry, "disruptor", SMOKE_NAMESPACE);
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
}
