package io.ddd4j.mq.kafka.mq;

import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.publish.MQEventPublisher;
import io.ddd4j.mq.serialization.MQMessageSerialization;
import io.ddd4j.mq.spi.MQBrokerAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * ddd4j Kafka MQ 适配层自动配置。
 */
@Configuration(proxyBeanMethods = false)
public class Ddd4jKafkaMQAutoConfiguration {

    /**
     * 注册 Kafka {@link MQBrokerAdapter} Bean。
     */
    @Bean
    public MQBrokerAdapter kafkaMQBrokerAdapter(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate,
            ObjectProvider<ConsumerFactory<String, String>> consumerFactory,
            ObjectProvider<MQMessageSerialization> serialization) {
        return new KafkaMQBrokerAdapter(
                kafkaTemplate.getIfAvailable(),
                consumerFactory.getIfAvailable(),
                serialization.getIfAvailable());
    }

    /**
     * 注册领域事件发布 Bean（与 Rabbit 等 cmpt 模块对齐）。
     */
    @Bean
    public MQEventPublisher kafkaMQEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            MQMessageSerialization serialization,
            Ddd4jMQProperties properties) {
        return new KafkaMQEventPublisher(kafkaTemplate, serialization, properties);
    }
}
