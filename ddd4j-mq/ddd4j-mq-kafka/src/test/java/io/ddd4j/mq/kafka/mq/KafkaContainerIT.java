package io.ddd4j.mq.kafka.mq;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.spring.config.Ddd4jMQPropertiesConfiguration;
import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.publish.MQEventPublisher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Kafka 发布路径 Testcontainers 冒烟集成测试（纯 Spring Framework + spring-kafka，无 Boot）。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        Ddd4jMQPropertiesConfiguration.class,
        KafkaContainerIT.KafkaInfrastructureConfiguration.class,
        Ddd4jKafkaMQAutoConfiguration.class
})
@EnabledIf("io.ddd4j.mq.kafka.mq.KafkaContainerIT#isDockerAvailable")
class KafkaContainerIT {

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    @Autowired
    private MQEventPublisher mqEventPublisher;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 启动 Kafka 容器。
     */
    @BeforeAll
    static void startKafka() {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ddd4j.mq.enabled", () -> "true");
        registry.add("ddd4j.mq.broker", () -> "kafka");
        registry.add("ddd4j.mq.namespace", () -> "it");
        registry.add("ddd4j.mq.test.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /**
     * Docker 是否可用（Testcontainers 前置条件）。
     */
    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    @Test
    void publishShouldNotThrow() {
        assertNotNull(mqEventPublisher);
        assertNotNull(kafkaTemplate);

        DemoPublishEvent event = new DemoPublishEvent();
        event.setTopic("smoke");
        event.setTag("ping");
        event.setTenantId("tenant-it");

        assertDoesNotThrow(() -> mqEventPublisher.publish(
                event,
                MQDestination.of("smoke", "ping", "it")));
    }

    /**
     * Kafka 基础设施（替代 Boot {@code KafkaAutoConfiguration}）。
     */
    @Configuration(proxyBeanMethods = false)
    static class KafkaInfrastructureConfiguration {

        /**
         * 构建生产者工厂。
         */
        @Bean
        ProducerFactory<String, String> producerFactory(
                @Value("${ddd4j.mq.test.kafka.bootstrap-servers}") String bootstrapServers) {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new DefaultKafkaProducerFactory<>(props);
        }

        /**
         * 注册 Kafka 发送模板。
         */
        @Bean
        KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }

        /**
         * 构建消费者工厂（Broker 适配层可选依赖）。
         */
        @Bean
        ConsumerFactory<String, String> consumerFactory(
                @Value("${ddd4j.mq.test.kafka.bootstrap-servers}") String bootstrapServers) {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "ddd4j-it");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            return new DefaultKafkaConsumerFactory<>(props);
        }
    }

    static class DemoPublishEvent extends MQEvent {
    }
}
