package io.ddd4j.mq.kafka;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Properties;

/**
 * Kafka adapter configuration.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class KafkaMQProperties {

    private String bootstrapServers = "localhost:9092";
    private String clientId = "ddd4j-mq-kafka";
    private String groupIdPrefix = "ddd4j";
    private Duration pollTimeout = Duration.ofSeconds(1);
    private boolean autoStartConsumers = true;

    public Properties producerProperties() {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return properties;
    }

    public Properties consumerProperties(String groupId) {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId + "-consumer");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return properties;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getGroupIdPrefix() {
        return groupIdPrefix;
    }

    public void setGroupIdPrefix(String groupIdPrefix) {
        this.groupIdPrefix = groupIdPrefix;
    }

    public Duration getPollTimeout() {
        return pollTimeout;
    }

    public void setPollTimeout(Duration pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    public boolean isAutoStartConsumers() {
        return autoStartConsumers;
    }

    public void setAutoStartConsumers(boolean autoStartConsumers) {
        this.autoStartConsumers = autoStartConsumers;
    }
}
