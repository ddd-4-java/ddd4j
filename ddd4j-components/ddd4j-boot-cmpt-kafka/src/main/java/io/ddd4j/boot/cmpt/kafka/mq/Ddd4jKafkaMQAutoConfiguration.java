package io.ddd4j.boot.cmpt.kafka.mq;

import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.publish.MQEventPublisher;
import io.ddd4j.boot.mq.serialization.MQMessageSerialization;
import io.ddd4j.boot.mq.spi.MQBrokerAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * ddd4j Kafka MQ 适配层自动配置。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({KafkaTemplate.class, MQBrokerAdapter.class})
@ConditionalOnExpression("${ddd4j.mq.enabled:false} == true && '${ddd4j.mq.broker:none}'.equals('kafka')")
@EnableConfigurationProperties(Ddd4jMQProperties.class)
@AutoConfigureAfter(KafkaAutoConfiguration.class)
public class Ddd4jKafkaMQAutoConfiguration {

    /**
     * 注册 Kafka {@link MQBrokerAdapter} Bean。
     */
    @Bean
    @ConditionalOnMissingBean(name = "kafkaMQBrokerAdapter")
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
    @ConditionalOnMissingBean(MQEventPublisher.class)
    public MQEventPublisher kafkaMQEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            MQMessageSerialization serialization,
            Ddd4jMQProperties properties) {
        return new KafkaMQEventPublisher(kafkaTemplate, serialization, properties);
    }
}
