/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.mq.kafka;

import io.ddd4j.mq.MQProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Properties;

/**
 * Kafka adapter configuration.
 *
 * <p>{@link KafkaMQProperties} extends {@link MQProperties} —— 复用通用字段（namespace / concat /
 * defaultTopic / autoAck / persist / retries / username / password 等），仅声明 Kafka 专属字段。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KafkaMQProperties extends MQProperties {

    /**
     * 默认分区数（自动创建 topic）。
     */
    public static final int DEFAULT_TOPIC_PARTITIONS = 3;
    /**
     * 默认副本数。
     */
    public static final short DEFAULT_TOPIC_REPLICATION = 1;

    /**
     * Kafka broker bootstrap servers（独立于父类 server 以遵循 Kafka 命名约定）。
     */
    private String bootstrapServers = "localhost:9092";
    /** \u8fde\u63a5 Kafka \u5ba2\u6237\u7aef\u65f6\u4f7f\u7528\u7684\u6807\u8bc6\u3002 */
    private String clientId = "ddd4j-mq-kafka";
    /** \u62fc\u63a5\u903b\u8f91\u6d88\u8d39\u8005\u7ec4 ID \u65f6\u4f7f\u7528\u7684\u540d\u79f0\u524d\u7f00\u3002 */
    private String groupIdPrefix = "ddd4j";
    /** \u6bcf\u6b21 Kafka \u8f6e\u8be2\u6d88\u606f\u6240\u7528\u7684\u8d85\u65f6\u3002 */
    private Duration pollTimeout = Duration.ofSeconds(1);
    /** \u662f\u5426\u5728\u521d\u59cb\u5316\u540e\u81ea\u52a8\u542f\u52a8 Kafka \u6d88\u8d39\u5faa\u73af\u3002 */
    private boolean autoStartConsumers = true;

    /**
     * 是否在 initProducer 时通过 AdminClient 自动创建缺失的 topic（借鉴 4）。
     */
    private boolean autoCreateTopics = true;

    /**
     * 自动创建 topic 时的分区数（仅当 autoCreateTopics=true 生效）。
     */
    private int defaultTopicPartitions = DEFAULT_TOPIC_PARTITIONS;

    /**
     * 自动创建 topic 时的副本数（仅当 autoCreateTopics=true 生效，生产集群建议 ≥3）。
     */
    private short defaultTopicReplication = DEFAULT_TOPIC_REPLICATION;
    /**
     * 发布确认等待超时（ms）。Outbox 模式下 publish 会同步阻塞至 broker ack 或超时；
     * 超时后会抛 IllegalStateException 让上层 Outbox 重试，避免无限阻塞耗尽线程池。
     * 默认 30s，与 Kafka {@code REQUEST_TIMEOUT_MS_CONFIG} 保持一致。
     */
    private long publishAckTimeoutMillis = 30_000L;

    /**
     * Producer 配置（对齐 ProducerConfig 常量名）
     * @return 返回的 Properties 结果
     */
    public Properties producerProperties() {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        properties.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432L);
        return properties;
    }

    /**
     * AdminClient 配置
     * @return 返回的 Properties 结果
     */
    public Properties adminProperties() {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(AdminClientConfig.CLIENT_ID_CONFIG, clientId + "-admin");
        return properties;
    }

    /**
     * Consumer 配置
     * @param groupId 分组标识
     * @return 返回的 Properties 结果
     */
    public Properties consumerProperties(String groupId) {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId + "-consumer");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        properties.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        properties.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        return properties;
    }
}
