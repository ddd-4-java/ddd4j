package io.ddd4j.mq.kafka.config;

import lombok.Data;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka 连接配置（Spring Framework 层，不依赖 Spring Boot {@code KafkaProperties}）。
 * <p>
 * 由应用或 boot 轨 AutoConfiguration 绑定 {@code spring.kafka.*} / 自定义前缀后注入。
 * </p>
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class KafkaConnectionProperties {

    /** Kafka bootstrap 地址，如 {@code localhost:9092} */
    private String bootstrapServers;

    /** 默认 topic（对应 Boot {@code spring.kafka.template.default-topic}） */
    private String defaultTopic;

    /** 事务生产者 ID 前缀（对应 Boot {@code spring.kafka.producer.transaction-id-prefix}） */
    private String transactionIdPrefix;

    /** 额外 consumer 参数 */
    private Map<String, String> consumer = new HashMap<>();

    /** 额外 producer 参数 */
    private Map<String, String> producer = new HashMap<>();

    /** 额外 admin 参数 */
    private Map<String, String> admin = new HashMap<>();

    /** SSL 相关参数（key 为 Kafka client 配置项） */
    private Map<String, String> ssl = new HashMap<>();

    /**
     * 构建 Kafka Consumer 客户端配置。
     *
     * @return consumer 配置 Map
     */
    public Map<String, Object> buildConsumerProperties() {
        Map<String, Object> props = new HashMap<>();
        putBootstrapServers(props);
        mergeStringMap(props, consumer);
        mergeStringMap(props, ssl);
        return props;
    }

    /**
     * 构建 Kafka Producer 客户端配置。
     *
     * @return producer 配置 Map
     */
    public Map<String, Object> buildProducerProperties() {
        Map<String, Object> props = new HashMap<>();
        putBootstrapServers(props);
        mergeStringMap(props, producer);
        mergeStringMap(props, ssl);
        return props;
    }

    /**
     * 构建 Kafka Admin 客户端配置。
     *
     * @return admin 配置 Map
     */
    public Map<String, Object> buildAdminProperties() {
        Map<String, Object> props = new HashMap<>();
        putBootstrapServers(props);
        mergeStringMap(props, admin);
        mergeStringMap(props, ssl);
        return props;
    }

    private void putBootstrapServers(Map<String, Object> props) {
        if (bootstrapServers != null && !bootstrapServers.isBlank()) {
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        }
    }

    private static void mergeStringMap(Map<String, Object> target, Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((key, value) -> {
            if (Objects.nonNull(key) && Objects.nonNull(value)) {
                target.put(key, value);
            }
        });
    }
}
