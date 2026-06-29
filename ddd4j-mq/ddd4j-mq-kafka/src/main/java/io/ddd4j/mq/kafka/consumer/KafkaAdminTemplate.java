package io.ddd4j.mq.kafka.consumer;

import io.ddd4j.mq.kafka.config.KafkaConnectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.*;

@Slf4j
public class KafkaAdminTemplate {

    private final KafkaConnectionProperties properties;
    private volatile AdminClient adminClient;

    public KafkaAdminTemplate(KafkaConnectionProperties properties) {
        this.properties = properties;
    }

    /**
     * AdminClient 的配置参数
     *
     * @return KafkaProducer 的配置参数
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     */
    public Map<String, Object> defaultAdminConfigs() {
        Map<String, Object> props = new HashMap<>(properties.buildAdminProperties());
        // key 和 value 的序列化方式
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.putIfAbsent("spring.json.trusted.packages", "*");
        return props;
    }

    public AdminClient getAdminClient() {
        if (adminClient == null) {
            synchronized (KafkaAdminTemplate.class) {
                if (adminClient == null) {
                    adminClient = AdminClient.create(defaultAdminConfigs());
                }
            }
        }
        return adminClient;
    }

    public ListTopicsResult listTopics() {
        return getAdminClient().listTopics();
    }

    /**
     * 创建一个Kafka消费者
     *
     * @param topic 消费者订阅的话题
     * @return 消费者对象
     */
    public boolean createTopic(String topic) {
        try {
            log.info("init kafkaTopic : {}", escapeBackslashes(topic));
            ListTopicsResult listTopicsResult = listTopics();
            boolean isE = listTopicsResult.names().get().stream().anyMatch(existingTopicName -> existingTopicName.equals(escapeBackslashes(topic)));
            if (!isE) {
                // 创建新的话题
                CreateTopicsResult result = getAdminClient().createTopics(Collections.singleton(new NewTopic(topic, 1, (short) -1)));
                return Objects.nonNull(result.all().get());
            } else {
                log.info("Topic {} already exists", escapeBackslashes(topic));
                Optional<TopicListing> topicListing = listTopicsResult.namesToListings().get().entrySet().stream()
                        .filter(entry -> entry.getKey().equals(escapeBackslashes(topic)))
                        .map(Map.Entry::getValue)
                        .findFirst();
                if (topicListing.isPresent()) {
                    return Boolean.TRUE;
                }
            }
        } catch (Exception e) {
            log.error("init kafkaTopic {} error : {}", escapeBackslashes(topic), ExceptionUtils.getStackTrace(e));
        }
        return Boolean.FALSE;
    }

    public String escapeBackslashes(String str) {
        return (str == null ? "" : str.replaceAll("/", "-"));
    }
}
